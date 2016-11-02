
package thmp;

import java.util.ArrayList;
import java.util.List;

/*
 * Pair of strings
 */

public class Pair {

	private String word; //the actual word occurring in sentence
	//adj, noun, pronoun, symb
	private String pos; 
	//list of additional parts of speech
	private List<String> extraPosList;
	
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
	public List<String> extraPosList(){
		return this.extraPosList;
	}
	
	/**
	 * @return Additional parts of speech
	 */
	public void addExtraPos(String pos){
		//lazy initialization with double locking
		if(extraPosList == null){
			synchronized(this){
				if(extraPosList == null){
				extraPosList = new ArrayList<String>();
				}
			}
		}		
		this.extraPosList.add(pos);
	}
	
	@Override
	public String toString(){
		return "[" + this.word + ", " + this.pos + "]";
	}
	
	public void set_word(String newWord){
		this.word = newWord;
	}

}
