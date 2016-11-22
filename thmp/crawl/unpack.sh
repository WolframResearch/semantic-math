
#!/bin/sh                                                                                                                                                                                
#untar files
list=arXiv_src_1006_006.tar
createdDir=()

for entry in arXiv_src_1006_006.tar
do
    #create content directory for entry:
    mkdir "${entry:10:8}""Untarred"
    tar -xvf "$entry" -C "${entry:10:8}""Untarred" 2>&1 >/dev/null
    createdDir+=("${entry:10:8}""Untarred")
done

for entry in "$createdDir"
do
    java -cp .:thmParse.jar thmp.crawl.UnzipFile2 "$entry""/1006"
done