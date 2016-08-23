package thmp.qa;

import java.util.Scanner;

import com.wolfram.jlink.ExprFormatException;
import com.wolfram.jlink.KernelLink;
import com.wolfram.jlink.MathLinkException;

import thmp.qa.AnswerParse.AnswerState;
import thmp.qa.QuestionAcquire.Formula;

/**
 * Contains main(), user interface.
 * Find which question to ask next. Keep track of what has been 
 * answered for each prompt.
 * 
 * @author yihed
 *
 */
public class QuestionSearch {

	//private static KernelLink ml;	

	
	/**
	 * Takes user's initial input, gets to the right context.
	 * @param args.
	 * @throws ExprFormatException 
	 * @throws MathLinkException 
	 * @throws ClassNotFoundException 
	 * @throws IllegalArgumentException 
	 */
	public static void main(String[] args) throws MathLinkException, ExprFormatException, IllegalArgumentException, ClassNotFoundException{
		
		System.out.println("What do you wanna find out?");
		
		Scanner sc = new Scanner(System.in);
		
		String initialReply = sc.nextLine();
		
		//process initial reply, prod more until arrive at some context.
		AnswerState answerState = AnswerParse.processInitial(initialReply);
		
		//try to fill in variables in the formula
		while(sc.hasNextLine()){
			String nextLine = sc.nextLine();
			
			//parseInput processes the input, 
			//updates the current AnswerState with information such 
			//as what to ask next. 
			answerState = AnswerParse.parseInput(nextLine, answerState);
			
			String response = answerState.nextQuestion();
			
			System.out.println(response);
			
		}
		
		sc.close();
	}
}
