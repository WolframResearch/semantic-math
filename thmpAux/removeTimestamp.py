import re

"""
Script used to remove timestamps from paths. 
for input in for combineMatrix.sh, 
"""

fromFile = open("timestamps.txt", "r")
#toFile = open("combineMatrixFileNames.txt", "w+")
toFile = open("test1.txt", "w+")

str = "usr/bin/juntimestamp"
tsPattern = re.compile("(.+/)[^/]+timestamp")
#print tsPattern.match(str).group(1)

output = ""
for line in fromFile:
    #newStr = line[-13:-5]
    #newStr = "".join([newStr,"Untarred/",newStr[0:4]])
    newStr = tsPattern.match(line).group(1)
    #print("newStr joined with Untarred "+newStr)
    #print("newStr: "+newStr)
    output = "".join([output, newStr, "\n"])
    
toFile.write(output)
