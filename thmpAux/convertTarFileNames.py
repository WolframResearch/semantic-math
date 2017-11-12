
"""
Script used to convert paths to tar files to file names appropriate
for input in for combineMatrix.sh, for example: from
/prospectus/crawling/_arxiv/src/arXiv_src_0505_002.tar
 to 0505_002Untarred/0505
"""

fromFile = open("test2.txt", "r")
fromFile = open("tarFileNames22.txt", "r")
fromFile = open("tarFileNames22.txt", "r")
#toFile = open("combineMatrixFileNames.txt", "w+")
toFile = open("testAugTo.txt", "w+")
str = ""

for line in fromFile:
    newStr = line[-13:-5]
    newStr = "".join([newStr,"Untarred/",newStr[0:4]])
    #print("newStr joined with Untarred "+newStr)
    #newStr = newStr.join([newStr[0:4]])
    #print("newStr: "+newStr)
    str = "".join([str, newStr, "\n"])
    
toFile.write(str)
