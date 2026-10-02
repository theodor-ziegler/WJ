extends PanelContainer

@onready var container: HBoxContainer = $RootMargin/RootRaise/RootSplit
@onready var sidebar: VBoxContainer = $RootMargin/RootRaise/RootSplit/SidebarSplit
@onready var label: MarkdownLabel = $RootMargin/RootRaise/RootSplit/MessageMargin/MessageMDL
@onready var icon: TextureRect = $RootMargin/RootRaise/RootSplit/SidebarSplit/IconCenter/IconTexture
@onready var sender_label: Label = $RootMargin/RootRaise/InfoSplit/SenderLabel
@onready var index_label: Label = $RootMargin/RootRaise/InfoSplit/IndexLabel

func move_icon(_icon_index: int) -> void:
	#print("[DEB] [CHB] Moving Icon To: ", _icon_index)
	container.move_child(sidebar, _icon_index)

func set_content(_text: String) -> void:
	#print("[DEB] [CHB] Setting Content To: " + _text)
	label.text = _text

func add_content(_text: String) -> void:
	label.text = label.text + _text

func set_icon(_icon: Texture) -> void:
	#print("[DEB] [CHB] Setting Icon To: ", _icon)
	icon.texture = _icon

func set_sender(_name: String) -> void:
	#print("[DEB] [CHB] Setting Sender To: " + _name)
	sender_label.text = _name

func get_sender() -> String:
	#print("[DEB] [CHB] Returning Sender: " + sender_label.text)
	return str(sender_label.text)

func set_index(_index: int) -> void:
	#print("[DEB] [CHB] Setting Index To: ", _index)
	index_label.text = str(_index)

func _on_copy_button_pressed() -> void:
	DisplayServer.clipboard_set(label.text)
