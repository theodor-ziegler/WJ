extends Object
class_name EntryObject

var id: int
var date: Vector3i
var title: String
var content: String

func _init(_id: int, _date: Vector3i, _title: String, _content: String) -> void:
	id = _id
	date = _date
	title = _title
	content = _content
