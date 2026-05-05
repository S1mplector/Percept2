#!/bin/bash
ICONS=(
"skip_previous"
"pause"
"loop"
"vertical_align_bottom"
"vertical_align_top"
"content_paste"
"control_point_duplicate"
"library_add"
"input"
"emoji_people"
"zoom_out_map"
"my_location"
"fast_rewind"
"fast_forward"
"wrap_text"
"format_align_justify"
"swap_horiz"
"open_in_full"
"close_fullscreen"
"folder_zip"
"grid_4x4"
"fiber_smart_record"
"border_all"
"join_inner"
"360"
"explore"
)

for icon in "${ICONS[@]}"; do
  echo "Fetching $icon..." >&2
  SVG=$(curl -s "https://raw.githubusercontent.com/marella/material-design-icons/main/svg/round/${icon}.svg")
  PATH_D=$(echo "$SVG" | grep -o 'd="[^"]*"' | sed 's/d="//' | sed 's/"//')
  # If there are multiple paths, join them with space
  PATH_D=$(echo "$PATH_D" | tr '\n' ' ')
  
  # output Java code
  VAR_NAME=$(echo "$icon" | tr '[:lower:]' '[:upper:]' | sed 's/360/THREE_SIXTY/')
  METHOD_NAME=$(echo "$icon" | awk -F_ '{for(i=1;i<=NF;i++){if(i==1){printf "%s", $i}else{printf "%s%s", toupper(substr($i,1,1)), substr($i,2)}}}' | sed 's/360/threeSixty/')
  
  echo "private static final String PATH_${VAR_NAME} = \"${PATH_D%% }\";"
  echo "public static Region ${METHOD_NAME}() { return ${METHOD_NAME}(\"#b0b8c8\"); }"
  echo "public static Region ${METHOD_NAME}(String color) { return icon(PATH_${VAR_NAME}, color, 14); }"
  echo ""
done
