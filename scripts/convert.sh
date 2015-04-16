for d in */ ; do
   while read line           
	do
		key=`echo "$line" | cut -d'=' -f1`
		value=`echo "$line" | cut -d'=' -f2`
    		if [ $key"/" == $d ]
		then
			mv $d $value
		fi
	done < convert.properties
done
