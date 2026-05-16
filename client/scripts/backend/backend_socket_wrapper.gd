class_name BackendSocketWrapper

signal on_connect(loaded_context: Array[MessageObject])
signal on_finish_generation
signal reasoning_begin
signal reasoning_end
signal new_reasoning_token(token: String)
signal new_normal_token(token: String)
signal new_tool_call_begins
signal tool_call_ended(tool_name: String)
signal ping_response
signal context_deleted
signal connection_fail
signal title_set
signal context_list(list: Dictionary[int, String])

const SERVER: String = "neuros.click"
const PORT: int = 3737

var client: StreamPeerTCP
var _output_thread: Thread
var _input_thread: Thread
var _output_lock: Mutex = Mutex.new()
var _send_queue: String = ""
var _connection_thread: Thread

func connect_socket() -> void:
	_connection_thread = Thread.new()
	_connection_thread.start(f_connection_thread)

func _number_hex_pad(number: int) -> String:
	var string: String = "%X" % number
	
	if string.length() < 16:
		for i: int in range(0, 16 - string.length()):
			string = "0" + string
	
	return string

func f_connection_thread() -> void:
	client = StreamPeerTCP.new()
	client.big_endian = true
	client.connect_to_host(SERVER, PORT)
	client.poll()
	
	while client.get_status() == StreamPeerTCP.STATUS_CONNECTING:
		client.poll()
		OS.delay_msec(10)

	if client.get_status() != StreamPeerTCP.STATUS_CONNECTED:
		print("[DEB] [BCK] Connection failed!")
		connection_fail.emit()
		return
	
	_output_thread = Thread.new()
	_input_thread = Thread.new()
	_output_thread.start(f_output_thread)
	_input_thread.start(f_input_thread)

func f_output_thread() -> void:
	client.poll()
	while true:
		OS.delay_msec(1)
		_output_lock.lock()
		if _send_queue == "":
			_output_lock.unlock()
			continue
		
		var local_send_queue: String = _send_queue
		_send_queue = ""
		_output_lock.unlock()
		client.put_string(local_send_queue)

func f_input_thread() -> void:
	while true:
		client.poll()
		if client.get_available_bytes() < 4:
			OS.delay_msec(1)
			continue
		
		var length: int = client.get_32()
		
		while client.get_available_bytes() < length:
			OS.delay_msec(1)
		
		var input_string: String = client.get_utf8_string(length)
		var server_return_type: String = input_string.substr(0, 1)
		var server_data_packet: String = input_string.substr(1, input_string.length() - 1)
		#print("Server:", server_return_type, "\nData:", server_data_packet)
		match server_return_type:
			"C":
				var output: Array[MessageObject] = []
				var json_data: Array = JSON.parse_string(server_data_packet)
				for element: Dictionary in json_data:
					if element["role"] == "user":
						output.append(MessageObject.new(MessageObject.Roles.USR, element["content"]))
					elif element["role"] == "assistant":
						output.append(MessageObject.new(MessageObject.Roles.LLM, element["content"]))
					else: print("WARNING: unknown data:", element)
				on_connect.emit.call_deferred(output)
			"F":
				print("Server finished generation")
				on_finish_generation.emit.call_deferred()
			"n":
				new_normal_token.emit.call_deferred(server_data_packet)
			"R":
				if server_data_packet == "start":
					reasoning_begin.emit.call_deferred()
				elif server_data_packet == "end":
					reasoning_end.emit.call_deferred()
			"t":
				tool_call_ended.emit.call_deferred(server_data_packet)
			"T":
				new_tool_call_begins.emit.call_deferred()
			"r":
				new_reasoning_token.emit.call_deferred(server_data_packet)
			"P":
				ping_response.emit.call_deferred()
			"D":
				context_deleted.emit.call_deferred()
			"h":
				title_set.emit.call_deferred()
			"l":
				var json_data: Dictionary = JSON.parse_string(server_data_packet)
				var out: Dictionary[int, String] = {}
				
				for key: String in json_data:
					out[key.to_int()] = json_data[key]
				
				context_list.emit.call_deferred(out)

func send_queue_string(string: String) -> bool:
	_output_lock.lock()
	if _send_queue != "":
		_output_lock.unlock()
		return false
	
	_send_queue = "Q" + string
	_output_lock.unlock()
	return true

func request_context(id: int) -> bool:
	_output_lock.lock()
	if _send_queue != "":
		_output_lock.unlock()
		return false
	
	_send_queue = "L" + str(id)
	_output_lock.unlock()
	return true

func queue_ping() -> bool:
	_output_lock.lock()
	if _send_queue != "":
		_output_lock.unlock()
		return false
	
	_send_queue = "P" + "ping"
	_output_lock.unlock()
	return true

func delete_context(id: int) -> bool:
	_output_lock.lock()
	if _send_queue != "":
		_output_lock.unlock()
		return false
	
	_send_queue = "D" + str(id)
	_output_lock.unlock()
	return true

func queue_set_title(index: int, title: String) -> bool:
	_output_lock.lock()
	if _send_queue != "":
		_output_lock.unlock()
		return false
	
	_send_queue = "t" + _number_hex_pad(index) + title
	_output_lock.unlock()
	return true

func queue_get_context_list() -> bool:
	_output_lock.lock()
	if _send_queue != "":
		_output_lock.unlock()
		return false
	
	_send_queue = "l" + " "
	_output_lock.unlock()
	return true
