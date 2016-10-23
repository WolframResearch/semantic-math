
#!/bin/sh                                                                                                                                                                                
for entry in "0001_2"
do
    java -cp .:thmParse.jar thmp.crawl.UnzipFile2 "$entry"
done