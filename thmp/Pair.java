
package thmp;

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
	private Set<String> extraPosSet;
	
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
	
	/**
	 * @return Additional parts of speech
	 */
	public Set<String> extraPosSet(){
		//System.out.println("!Getting extraPosList for pair" + this + " " + extraPosSet);
		//System.out.println(Arrays.toString(Thread.currentThread().getStackTrace()));
		return this.extraPosSet;
	}
	
	/**
	 * @return Additional parts of speech
	 */
	public void addExtraPos(String pos){
		//lazy initialization with double locking
		if(extraPosSet == null){
			synchronized(this){
				if(extraPosSet == null){
					extraPosSet = new HashSet<String>();
				}
			}
		}
		if(!pos.equals(this.pos)){
			this.extraPosSet.add(pos);
		}
	}
	
	@Override
	public String toString(){
		return "[" + this.word + ", " + this.pos + "]";
	}
	
	public void set_word(String newWord){
		this.word = newWord;
	}

}
