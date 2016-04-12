/*
 * Test class for ThmP1
 */
public class ThmP1Test {

	//the char of F_p is p
		public static void main(String[] args){
			ThmP1.buildMap();
			
			//ThmP1 p1 = new ThmP1();
			//String[] strAr = p1.preprocess("a disjoint or perfect field is a field".split(" "));
			//String[] strAr = p1.preprocess("quadratic extension has degree 2".split(" "));
			//String[] strAr = p1.preprocess("finite field is field".split(" "));
			//String[] strAr = p1.preprocess("field F extend field F".split(" "));
			
			//String[] strAr = p1.preprocess("a field or ring is a ring".split(" "));
			//String[] strAr = p1.preprocess("let T be any linear transformation ".split(" "));
			//String[] strAr = "let f be a linear transformation between V and W ".split(" ");
			//String[] strAr = "a linear transformation between V and W ".split(" ");
			//String[] strAr2 = "f is an invertible matrix".split(" ");
			//String[] strAr = p1.preprocess("if a field is ring then ring is ring".split(" "));
			//String[] strAr = p1.preprocess("a basis of a vector space consist of a set of linearly independent vectors".split(" "));
			//String[] strAr = p1.preprocess("finitely many vectors are called linearly independent if their sum is zero".split(" "));
			//String[] strAr = p1.preprocess("elements in symmetric group are conjugate if they have the same cycle type".split(" "));
			String[] strAr = ThmP1.preprocess("A is equal to the number of partitions".split(" "));
			strAr = "for all x x is a number".split(" ");
			strAr = "suppose f is measurable and finite on E, and E has finite measure".split(" ");
			strAr = "the number of conjugacy class of the symmetric group is equal to the number of partitions of n".split(" ");
			String st = "let H be a normal subgroup of the group G. G acts on H as automorphisms of H.";
			st = "the number of conjugacy class of the symmetric group is equal to the number of partitions of n";
			//st = "let G be a group, then G is a group";
			
			//String[] strAr = p1.preprocess("F is a extension over Q".split(" "));
			
			strAr = st.split("\\,|\\.");
			for(int i = 0; i < strAr.length; i++){
				ThmP1.parse(ThmP1.tokenize(ThmP1.preprocess(strAr[i].trim().split(" ")))); //p1.parse(p1.tokenize(p1.preprocess(strAr2)));
			}
			
			//p1.parse(p1.tokenize(p1.preprocess("characteristic of Fp is p".split(" "))));
						
		}
		
}
