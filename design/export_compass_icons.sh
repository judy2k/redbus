#!/bin/bash

DEST_DIR=../res

SOURCE_FILE=compass.svg

IDS="compass_n compass_ne compass_e compass_se compass_s compass_sw compass_w compass_nw stop_diverted stop_outoforder stop_unknown"
DENSITIES=("ldpi" "mdpi" hdpi xhdpi)
SIZES=(8 10 15 20)


for (( density_i=0 ; density_i < 4 ; density_i++ ))
do
    density=${DENSITIES[$density_i]}
    size=${SIZES[$density_i]}
    dir=${DEST_DIR}/drawable-${density}

    # Ensure dest exists:
    if [ ! -d $dir ]; then
        mkdir -p $dir
    fi

    for id in $IDS
    do
        dest_file=${dir}/${id}.png
        /Applications/Inkscape.app/Contents/Resources/bin/inkscape \
            --without-gui -i ${id} -e ${dest_file} -w ${size} -h ${size}  $SOURCE_FILE
    done
done
