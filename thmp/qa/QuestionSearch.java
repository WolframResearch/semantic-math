package thmp.qa;

import java.util.Scanner;

/**
 * Find which question to ask next. Keep track of what has been 
 * answered for each prompt.
 * 
 * @author yihed
 *
 */
public class QuestionSearch {

	/**
	 * Processes the initial input, figures out context.
	 * @param initialReply
	 * @param sc
	 */
	private static Formula processInitial(String initialReply, Scanner sc){
		
	}
	
	/**
	 * Takes user's initial input, gets to the right context.
	 * @param args.
	 */
	public static void main(String[] args){
		
		System.out.println("What do you wanna find out?");
		
		Scanner sc = new Scanner(System.in);
		
		String initialReply = sc.nextLine();
		//process initial reply, prod more until arrive at some context.
		Formula formula = processInitial(initialReply, sc);
		
		//try to fill in variables in the formula
		while(sc.hasNextLine()){
			String nextLine = sc.nextLine();
			
			//calls parseInput
			AnswerParse.parseInput(nextLine, formula);
		}
		
		sc.close();
	}
}
