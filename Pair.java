package thmp;
/*
 * Pair of strings
 */

public class Pair {

	private String word; //the actual word occurring in sentence
	//adj, noun, pronoun, symb
	private String pos; 
	
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
	
	@Override
	public String toString(){
		return word + " " + pos;
	}
}
