#!/bin/sh
printf '\033c\033]0;%s\a' Client-Github
base_path="$(dirname "$(realpath "$0")")"
"$base_path/Client.x86_64" "$@"
