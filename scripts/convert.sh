# https://crowdin.com/project_actions/export_project?project_id=10077
# https://crowdin.com/project_actions/check_export_status?project_id=10077
# https://crowdin.com/download/project/afwall.zip
# Convert exported crowdin files to android format
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
#delete Playstore.txt files
find . -name "Playstore.txt" -print0 | xargs -0 rm -rf

