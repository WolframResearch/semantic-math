
package thmp.parse;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/*
 * Pair of strings
 */

public class Pair {

	private String word; //the actual word occurring in sentence
	//part of speech, e.g. adj, noun, pronoun, symb
	private String pos; 
	//Set of additional parts of speech, set instead of list, 
	//to avoid accidental duplicates, which contribute to parse explosions.
	private Set<String> extraPosSet = new HashSet<String>();
	//index in the token list without TeX, for syntaxnet.
	private int noTexTokenListIndex;
	
	public Pair(String word, String pos){
		this.word = word;
		this.pos = pos;
	}
	
	public String word(){
		return word;
	}
	
	public String pos(){
		return pos;
	}
	
	public void set_pos(String newPos){
		this.pos = newPos;
	}

	public void set_word(String newWord){
		this.word = newWord;
	}
	
	public void setNoTexTokenListIndex(int index){
		this.noTexTokenListIndex = index;
	}
	/**
	 * index in the token list without TeX, for syntaxnet.
	 * @return
	 */
	public int noTexTokenListIndex(){
		return this.noTexTokenListIndex;
	}
	/**
	 * @return Additional parts of speech
	 */
	public Set<String> extraPosSet(){
		return this.extraPosSet;
	}
	
	/**
	 * @return Additional parts of speech
	 */
	public void addExtraPos(String pos){
		//lazy initialization with double locking.
		//This should never be called by two different threads though.
		/*if(extraPosSet == null){
			synchronized(this){
				if(extraPosSet == null){
					extraPosSet = new HashSet<String>();
				}
			}
		}*/
		if(!pos.equals(this.pos)){
			this.extraPosSet.add(pos);
		}
	}
	
	@Override
	public String toString(){
		StringBuilder sb = new StringBuilder(30);
		sb.append("[").append(this.word).append(", ").append(this.pos);		
		if(null != extraPosSet){
			sb.append(extraPosSet);
		}		
		sb.append(", ").append(noTexTokenListIndex);
		sb.append("]");
		return sb.toString();
		
	}
	

}
