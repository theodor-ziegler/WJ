extends Object
class_name MessageObject

enum Roles { LLM, USR }

var role: Roles
var content: String

func _init(_role: Roles, _content: String) -> void:
  role = _role
  content = _content
