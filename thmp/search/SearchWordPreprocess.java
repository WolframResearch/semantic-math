package thmp.search;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Maps;

import thmp.ThmP1;
import thmp.utils.WordForms;

/**
 * Preprocesses keywords before incorportating them into the termDocMx.
 * E.g. label word as part of hypothesis or conclusion.
 * @author yihed
 *
 */
public class SearchWordPreprocess {

	//private static final ImmutableMap<String, Integer> wordsScoreMap = CollectThm.get_wordsScoreMapNoAnno();
	//make "hyp" and "stm" into constants!
	private static final String HYP = "hyp";
	private static final String STM = "stm";
	//used in auxiliary list when labeling trigger words
	private static final int WHERE = 1;
	private static final int EXTRA_POINTS = 1;
	//set of trigger words indicating need to go further, and how many steps further. Eg "for every"
	private static final ImmutableMap<String, Integer> multipleWordTriggersMap;
	private static final String[] multipleWordTriggers = new String[]{"for", "1"};
	private static final String PUNCTUATION = ".,!:;";
	//regex for matching hypothesis words, such as let, suppose etc.
	//this is because Maps.posMap does not always label these words as "hyp" for 
	//parsing purposes.
	private static final String HYP_WORDS = "let|if|where|when";
	//trigger words indicating that the prior words are stm keywords
	//eg "where primality is in conclusion"
	private static final String STM_WORDS = "conclusion|result";
	
	static{
		Map<String, Integer> multipleWordTriggersPreMap = new HashMap<String, Integer>();
		
		for(int i = 0; i < multipleWordTriggers.length; i+=2){
			int numSteps = Integer.valueOf(multipleWordTriggers[i+1]);
			multipleWordTriggersPreMap.put(multipleWordTriggers[i], numSteps);
		}
		
		multipleWordTriggersMap = ImmutableMap.copyOf(multipleWordTriggersPreMap); 
		
	}
	/**
	 * 	Determine if hypothesis or conclusion.
	 *  Label words with prefixes, eg H for hypothesis, C for conclusion. Use ParserType.
	 *  Tell if hypothesis or conclusion, based on words such as if, suppose, therefore, etc.
	 *  Should run through parser, use ParseStructType label. 
	 *  @return return a representation of the word, like CRing.
	 */
	public static List<WordWrapper> sortWordsType(String inputStr){
		//read in the input tokens, sort into states, output array of words, labeled.
		//ie processed. 
		//reset type when a punctuation mark such as , or . is encountered,
		String[] inputAr = inputStr.toLowerCase().replaceAll("([^"+PUNCTUATION+"]*)(["+PUNCTUATION+"]{1})", "$1 $2")
				//.split("\\s+|\'|\\(|\\)|\\{|\\}|\\[|\\]");
				.split(WordForms.splitDelim());
		List<WordWrapper> wrapperList = new ArrayList<WordWrapper>();
		//auxiliary list of integers indicating the trigger words for 
		//labeling something as hyp word. Correspond to elements in wrapperList.
		List<Integer> hypTriggerWords = new ArrayList<Integer>();
		//use posMap to tell if hyp, etc
		//create wrapper object for each word
		//in hypothesis or not
		boolean inHyp = false;
		String hypTriggerWord = "";
		for(int i = 0; i < inputAr.length; i++){
			String word = inputAr[i];
			if(PUNCTUATION.contains(word)){
				inHyp = false;
				continue;
			}
			String wordPos;
			//look further than just current word to find its pos			
			Integer numSteps = multipleWordTriggersMap.get(word);
			if(numSteps != null && inputAr.length > i+1){
				//should use for loop, + numSteps
				String tempWord = word + " " + inputAr[i+1];
				if(thmp.Maps.getPos(tempWord) != null){					
					word = tempWord;
					i++;
				}
			}			
			
			if(!word.matches(HYP_WORDS)){
				wordPos = thmp.Maps.getPos(word);
				if(wordPos != null && wordPos.equals(HYP)){
					inHyp = true;
					hypTriggerWord = word;
					//these words should not count as keywords in thms.
					continue;
				}
			}else{
				inHyp = true;
				hypTriggerWord = word;
				continue;
			}
			
			WordWrapper wordWrapper;
			if(inHyp){
				//pos for wordWrapper could be stm or hyp.
				wordWrapper = new WordWrapper(HYP, word);
			}else{
				wordWrapper = new WordWrapper(STM, word);	
				hypTriggerWord = "";
			}
			wrapperList.add(wordWrapper);
			if(hypTriggerWord.matches("where|if|when")){
				hypTriggerWords.add(WHERE);
			}else{
				hypTriggerWords.add(0);			
			}
		}
		//go through wrapperList backwards, update conclusion words
		for(int i = wrapperList.size()-1; i > -1; i--){
			WordWrapper curWrapper = wrapperList.get(i);
			if(curWrapper.word().matches(STM_WORDS) && hypTriggerWords.get(i) == WHERE){
				while(i > -1 && hypTriggerWords.get(i) == WHERE){	
					curWrapper = wrapperList.get(i);
					curWrapper.set_pos(STM);
					//set extra points if pos matches in sentence.
					curWrapper.set_extraPoints(EXTRA_POINTS);
					i--;					
				}
			}
		}
		//System.out.print("wrapperList: "+wrapperList);
		return wrapperList;
	}
	
	/**
	 * 	Class for representing words with a context.
	 *	Represent context, 
	 */
	public static class WordWrapper{
		//part of speech, primarily hyp or stm
		private String pos;
		//word being wrapped
		private String word;
		//extra points for matching the pos in sentence.
		//default is 0
		private int matchExtraPoints;
		
		public WordWrapper(String pos, String word){
			this.pos = pos;
			this.word = word;
		}
		/**
		 * String hashed using other annocation. ie if 
		 * hyp, use stm, and vice versa. Used to perform search
		 * of same word, under different context.
		 * @return
		 */
		public String otherHashForm(){
			return otherHashForm(word);
		}

		public String otherHashForm(String curWord){
			String prefix = pos.equals(HYP) ? "C" : "H";
			return prefix + curWord;
		}
		
		/**
		 * Wrap around words based on context. 
		 * could also pass in long form.
		 */
		public String hashToString(){			
			return hashToString(word);
		}
		/**
		 * Hash this wrapper's context in the input word
		 */
		public String hashToString(String curWord){		
			//for now just do hyp or conclusion
			String prefix = pos.equals(HYP) ? "H" : "C";
			return prefix + curWord;
		}		
		
		public void set_extraPoints(int points){
			matchExtraPoints = points;
		}
		/**
		 * Extra points awarded if pos matches.
		 * @return
		 */
		public int matchExtraPoints(){
			return matchExtraPoints;
		}
		public void set_pos(String pos){
			this.pos = pos;
		}
		
		public String pos(){
			return pos;
		}
		
		public String word(){
			return word;
		}
		@Override
		public String toString(){
			return word + " " + pos;
		}
	}
	
}
