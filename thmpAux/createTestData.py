
'''
Create test data for char encodings
content="text/html; charset="..."

'''
import re
import sys

title_start_regex = re.compile("(<title>| title=\")")
title_end_regex = re.compile("</title>")

enc_regex1 = re.compile('content="text/html;\\s*charset=([^"]+)')
enc_regex2 = re.compile('<meta charset\\s*=\\s*\"([^"]+)')

min_char_count = 40

'''
read lines in file, get encoding and text of entire html page, 
produce json of encoding and titles elements.
Args: text file path. Need to read incrementally due to memory!

'''
def process_text(file_path):

    enc_dict = {}

    with open(file_path) as file:
        line = file.readline()
        lines_list = []
        in_html_bool = False

        cur_enc = ''
        while (line != ''):

            if not in_html_bool:

                m1 = enc_regex1.search(line)
                m2 = enc_regex2.search(line)
            
                if (m1 != None or m2 != None):
                    if (m1 != None):
                        cur_enc = m1.group(1)
                    else:
                        cur_enc = m2.group(1)
                    cur_enc = cur_enc.lower()
                    in_html_bool = True
                    #print("cur_enc! "+ cur_enc)
                    lines_list.append(line)
            elif "</html>" in line:
                in_html_bool = False
                
                text = ''.join(lines_list)
                title_str = get_title_elements(text)
                lines_list = []
                if len(title_str) >= min_char_count:
                    if cur_enc in enc_dict:
                        enc_dict[cur_enc].append(title_str)
                    else:
                        enc_dict[cur_enc] = [title_str]
                
            elif in_html_bool:
                lines_list.append(line)            
                
            line = file.readline()
    return enc_dict

'''
Function to take title elements out of an html page, until "</html>"
is encountered.
Returns title elements string, created from list of title elements in this string.
'''
def get_title_elements(text):
    #char-level parsing
    m_start = 0
    
    text_len = len(text)
    m = title_start_regex.search(text, m_start)
    titles_list = []
    while (m != None):
        index = m.end()
        match_content = m.group(1)
        if "<" in match_content:
            while index + 7 < text_len and text[index:index+8] != "</title>":
                index += 1
        else:
            while index < text_len and text[index] != '"':
                index += 1

        titles_list.append(text[m.end() : index])
        #print "new title content: "+ str(index)+" "+text[m.start() : index]
        #m_start = index
        m = title_start_regex.search(text, index)
        #print "m inside loop: "+str(m)
        
    return '\n'.join(titles_list)

'''
Write map's bytes to files for that encoding. 
'''
def write_map_bytes(map):
    
    for enc, sample_list in map.items():
        #html might have been inproperly written
        if len(sample_list) < 3 or '/' in enc or ';' in enc or "'" in enc:
            continue
        enc_str = '\n'.join(sample_list)
        print "enc and count: " + enc + " " + str(len(sample_list))
        with open("../data/" + enc+"_bytes.txt", "wb") as file:
            file.write(enc_str)

if __name__ == "__main__":

    argv = sys.argv
    if len(argv) < 2:
        print "Please supply a warc data source!"
    else:
        map = process_text(argv[1])
        #with open("../bytes.txt", "wb") as file:
        #    file.write(map["GB2312"][0])
        write_map_bytes(map)   

        #print "map: ",map
