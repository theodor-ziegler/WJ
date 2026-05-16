extends PanelContainer


@onready var chat_main: VBoxContainer = $RootMargin/RootSplit/MainSplit/SubSplit/ChatPanel/ChatMargin/ChatScroll/ChatMain
@onready var prompt_edit: TextEdit = $RootMargin/RootSplit/MainSplit/InputPanel/InputMargin/InputMain/PromptEdit
@onready var status_label: Label = $RootMargin/RootSplit/StatusInfoPanel/StatusInfoMargin/StatusInfoLabel
@onready var toolbar_panel: PanelContainer = $RootMargin/RootSplit/MainSplit/SubSplit/ToolbarPanel
@onready var chat_panel: PanelContainer = $RootMargin/RootSplit/MainSplit/SubSplit/ChatPanel
@onready var history_panel: PanelContainer = $RootMargin/RootSplit/MainSplit/SubSplit/HistoryPanel
@onready var history_main: VBoxContainer = $RootMargin/RootSplit/MainSplit/SubSplit/HistoryPanel/HistoryMargin/HistoryScroll/HistoryMain

var chatbubblepre: PackedScene = preload("res://scenes/chat_bubble.tscn")
var message_index: int = 0
var backend: BackendSocketWrapper = BackendSocketWrapper.new()
var current_bubble: Node = null
var ping_state: ping_states = ping_states.OFFLINE
var pings_missed = 0
var ping_miss_max = 2
var http_request:HTTPRequest = HTTPRequest.new()
var web_state: web_states = web_states.OFFLINE
var heartbeat_interval = 25
var historyentrypre: PackedScene = preload("res://scenes/history_entry.tscn")
var active_panel: panel_types = panel_types.CHAT

enum ping_states {ONLINE, WAITING, OFFLINE, DEAD}
enum web_states {ONLINE, WAITING, OFFLINE}
enum panel_types {CHAT, HISTORY}

var ping_status_indices = {
	ping_states.ONLINE: "Connected",
	ping_states.WAITING: "...",
	ping_states.OFFLINE: "Disconnected",
	ping_states.DEAD: "Connection Failed"
}
var web_status_indices = {
	web_states.ONLINE: "Online",
	web_states.WAITING: "...",
	web_states.OFFLINE: "Offline"
}


func _on_window_resize():
	self.size = get_viewport().get_visible_rect().size

func _ready() -> void:
	#Window
	self.size = get_viewport().get_visible_rect().size
	get_window().size_changed.connect(_on_window_resize)
	#Signals
	backend.on_connect.connect(_on_context_load)
	backend.on_finish_generation.connect(_on_server_complete_generation)
	backend.new_normal_token.connect(_on_new_normal_token)
	backend.new_reasoning_token.connect(_on_reasoning_token)
	backend.new_tool_call_begins.connect(_on_tool_call_begin)
	backend.tool_call_ended.connect(_on_tool_call_end)
	backend.reasoning_begin.connect(_on_begin_reasoning)
	backend.reasoning_end.connect(_on_end_reasoning)
	backend.ping_response.connect(_on_ping_return)
	backend.context_deleted.connect(_on_context_deleted)
	http_request.request_completed.connect(_on_web_return)
	backend.connection_fail.connect(_on_connection_fail)
	backend.context_list.connect(_on_backend_context_list_return)
	backend.title_set.connect(_on_backend_title_set_return)
	#Test
	#test_features()
	#Go Live
	add_child(http_request)
	backend.connect_socket()
	heartbeat(heartbeat_interval)
	open_panel(active_panel)

func add_message(_msg_obj: MessageObject) -> Node:
	message_index += 1
	print("[DEB] [MSG] New Message At Index: ", message_index)
	var msg: Node = chatbubblepre.instantiate()
	chat_main.add_child(msg)
	match _msg_obj.role:
		_msg_obj.Roles.LLM:
			msg.move_icon(0)
			msg.set_sender("Martin")
			print("[DEB] [MSG] Sender: " + msg.get_sender() + " @ Role: LLM")
		_msg_obj.Roles.USR:
			msg.move_icon(1)
			msg.set_sender("Student")
			print("[DEB] [MSG] Sender: " + msg.get_sender() + " @ Role: USR")
	msg.set_index(message_index)
	msg.set_content(_msg_obj.content)
	#print("[DEB] [MSG] Content: " + _msg_obj.content)
	return msg

func send_message() -> void:
	prompt_edit.editable = false
	var prompt_str: String = prompt_edit.text
	print("[DEB] [PRM] Sending Prompt String: " + prompt_str)
	var success: bool = backend.send_queue_string(prompt_str)
	if success: prompt_edit.text = ""
	var new_usr_msg_obj: MessageObject = MessageObject.new(MessageObject.Roles.USR, prompt_str)
	add_message(new_usr_msg_obj)
	var new_llm_msg_obj: MessageObject = MessageObject.new(MessageObject.Roles.LLM, "")
	current_bubble = add_message(new_llm_msg_obj)

func _on_context_load(_loaded_context: Array[MessageObject]) -> void:
	print("[DEB] [CON] Loading Context: ", len(_loaded_context), " Messages")
	open_panel(panel_types.CHAT)
	for message in _loaded_context:
		add_message(message)

func _on_ping_return() -> void:
	print("[DEB] [HRT] Pong! On LLM")
	ping_state = ping_states.ONLINE
	update_status_label()

func _on_web_return(_result, _response_code, _headers, _body):
	if _result == HTTPRequest.RESULT_SUCCESS:
		print("[DEB] [HRT] Pong! On Web")
		web_state = web_states.ONLINE
	else:
		print("[DEB] [HRT] Anti-Pong! On Web")
		web_state = web_states.OFFLINE
	update_status_label()

func _on_begin_reasoning() -> void:
	print("[DEB] [RES] Register Reasoning Begin") # TODO: Replace with something to begin a reasoning block visually

func _on_end_reasoning() -> void:
	print("[DEB] [RES] Register Reasoning End") # TODO: Replace with something to end a reasoning block visually

func _on_reasoning_token(token: String) -> void:
	print("[DEB] [TOK] New Reasoning Token: " + token) # TODO: Integrate with UI

func _on_context_deleted() -> void:
	pass # TODO

func _on_new_normal_token(_token: String) -> void:
	print("[DEB] [TOK] New Message Token: " + _token)
	current_bubble.add_content(_token)

func _on_tool_call_begin() -> void:
	print("[DEB] [TOL] New Tool Call") # TODO: Show some kind of tool call in progress visual

func _on_tool_call_end(_tool_name: String) -> void:
	print("[DEB] [TOL] Call To Tool: ", _tool_name, ", Ended") # TODO: Finish tool call block here

func _on_server_complete_generation() -> void:
	print("[DEB] [GEN] Generation Complete, Unlocking PromptEdit")
	prompt_edit.editable = true

func _on_send_button_pressed() -> void:
	print("[DEB] [PRM] Prompt Submitted Via SendButton")
	send_message()

func _on_prompt_edit_text_submitted(_new_text: String) -> void:
	print("[DEB] [PRM] Prompt Submitted Via PromptEdit")
	send_message()

func heartbeat(_interval_s: float):
	while true:
		print("[DEB] [HRT] Heartbeat")
		if ping_state != ping_states.DEAD:
			ping_state = ping_states.WAITING
		web_state = web_states.WAITING
		update_status_label()
		backend.queue_ping()
		http_request.request("https://www.google.com")
		await get_tree().create_timer(_interval_s).timeout
		if ping_state == ping_states.WAITING:
			pings_missed += 1
			if pings_missed > ping_miss_max:
				ping_state = ping_states.OFFLINE
		update_status_label()

func update_status_label():
	status_label.text = web_status_indices[web_state] + " | " + ping_status_indices[ping_state]

func _on_menu_button_pressed() -> void:
	toolbar_panel.visible = !toolbar_panel.visible

func _on_connection_fail() -> void:
	ping_state = ping_states.DEAD

func clear_chat_main() -> void:
	for child in chat_main.get_children():
		chat_main.remove_child(child)

func add_history_entry(_etr_obj: EntryObject) -> Node:
	print("[DEB] [HIS] New Entry At Index: ", _etr_obj.id)
	var etr: Node  = historyentrypre.instantiate()
	history_main.add_child(etr)
	etr.set_all(_etr_obj.date, _etr_obj.id, _etr_obj.title, _etr_obj.content)
	etr.opened_this.connect(on_history_context_open)
	return etr

func _on_history_button_pressed() -> void:
	open_panel(panel_types.HISTORY)
	#test_features()
	backend.queue_get_context_list()

func open_panel(_panel: panel_types):
	chat_panel.visible = false
	history_panel.visible = false
	match _panel:
		panel_types.CHAT:
			print("[DEB] [PAN] Opening Panel: CHAT")
			chat_panel.visible = true
		panel_types.HISTORY:
			print("[DEB] [PAN] Opening Panel: HISTORY")
			history_panel.visible = true

func on_history_context_open(_index):
	print("[DEB] [HIS] Opening Historic Context At Index: ", _index)
	backend.request_context(_index)

func _on_backend_context_list_return(_list: Dictionary[int, String]) -> void:
	#pass # TODO
	print("Backend COntaxt List Happened")
	var context_obj_list = []
	for entry in _list.keys():
		context_obj_list.append(EntryObject.new(entry, Vector3i(01,01,1970), _list[entry], "Not implemented"))
	for entry in context_obj_list:
		add_history_entry(entry)

func _on_backend_title_set_return() -> void:
	#pass # TODO
	print("Backend Title Set Return happened")

func test_features() -> void:
	print("[INF] [TST] Test Function Started - Not Release Ready!")
	var hisobj = EntryObject.new(0,Vector3i(01,01,1970),"Test Entry","This is a test!")
	add_history_entry(hisobj)
	
