
'''
Batch msc terms files from various directories
'''
import re
import subprocess
import os
import os.path

newline_patt = re.compile("\n")

'''
Batch files together, given a file containing list of \n-separated
relative paths to mscTerms.txt. Paths relative to ~/thm 
Args:
 filePathsStr is file containing paths
'''
def combineMscFiles(filePathsStr):

    batch_size = 50
    with open(filePathsStr, 'r') as file:
        paths = file.read()
    paths = paths.strip()
    pathsAr = newline_patt.split(paths)
    pathsArLen = len(pathsAr)
    totalIter = pathsArLen / batch_size + 1

    #strList = []
    
    file_counter = 0
    curDir = '/home/usr0/yihed/thm/'
    while file_counter < totalIter:
        #subprocess.check_call(("touch "+file_path).split())
        fileStr = ''
        for path in pathsAr[file_counter*batch_size : min((file_counter+1)*batch_size, pathsArLen)]:
            with open(path, 'r') as file:
                fileStr = ''.join([fileStr, ' ', file.read()])
            #path = curDir + path
            #cmd = "cat " + path+" >> " + curDir + "src/thmp/data/msc/mscTerms"+str(file_counter)+".txt"
            #print "cmd! ",cmd
            #subprocess.call(cmd.split())
        file_path = curDir + "src/thmp/data/msc/mscTerms"+str(file_counter)+".txt"
        #subprocess.call(("rm " + file_path).split())        
        if os.path.isfile(file_path):
            os.remove(file_path)
        with open(file_path, 'a+') as file:
            file.write(fileStr)            
        file_counter += 1
        
    #call cat file1 file2 ... > mscCombined.txt
    
