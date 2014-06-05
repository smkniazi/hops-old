
source ./../deployment.properties

if [ $HOP_Rebuild_Distro = true ]; then
    CMD1="mvn"
    CMD2="mvn"
    CMD3="mvn"
    if [ $HOP_Do_Clean_Build = true ]; then
	    CMD1="$CMD1 clean "
	    CMD2="$CMD2 clean "
	    CMD3="$CMD3 clean "
    fi
    CMD1="$CMD1 install -Dmaven.test.skip=true -f $HOP_Metadata_Dal_Folder"
    CMD2="$CMD2 assembly:assembly -f $HOP_Metadata_Dal_Impl_Folder"
    CMD3="$CMD3 package -Pdist -Dmaven.test.skip=true -f $HOP_Src_Folder"

 #run commands
 echo $CMD1
 echo $CMD2
 echo $CMD3
fi

