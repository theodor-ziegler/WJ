extends PanelContainer

@onready var title_label = $MarginContainer/MainSplit/Partitioner/TextMargin/TextSplit/TitleLabel
@onready var content_label = $MarginContainer/MainSplit/Partitioner/TextMargin/TextSplit/ContentMDL
@onready var date_label = $MarginContainer/MainSplit/InfoPanelSplit/DateLabel
@onready var index_label = $MarginContainer/MainSplit/InfoPanelSplit/IndexLabel

var index = 0

signal opened_this(index)

func set_all(_date: Vector3i, _index: int,_title: String, _content: String):
	title_label.text = _title
	content_label.text = _content
	date_label.text = str(_date)
	index_label.text = str(_index)
	index = _index


func _on_open_button_pressed() -> void:
	opened_this.emit(index)
