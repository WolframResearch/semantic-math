package thmp;

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
			st = "conjugate elements and conjugate subgroups have the same order";
			st = "A is a group and is a subgroup";
			st = "let G be a group, conjugation by g is called a automorphism of G";
			st = "if p is an odd prime and n is an integer, then the automorphism group of the cyclic group of order p is cyclic";
			st = "let p be a prime and let V be an abelian group, with the property that b is c, then V is an n dimensional vector space over the finite field";
			st = "the automorphism group of the cyclic group of order 2 is isomorphic to Z";
			//st = "the number of conjugacy class of the symmetric group is equal to the number of partitions of n";
			//st = "let G be a group, then G is a group";
			st = "let G be a group and let p be a prime, a group of order that is a power of p is called a p group";
			st = "a group with order that is a power of p is called a p group. a subgroup of G that is a p group is called a p subgroup. p subgroup.";
			st = "a group with order that is a power of p is defined to be a p subgroup of G";
			st = "the p subgroups of G are denoted by Syl";
			st = "a subgroup of order a power of p is called a p subgroup, the number of p subgroup of G is 2"; //or "of the form p^k"
			st = "the number of p subgroup of G is 2";
			st = "subgroups of G exist";
			st = "there exists a finite semiring with order 11";
			st = "n is the index in G of the normalizer for any p subgroup";
			st = "let G be a group of order p, where p is a prime not dividing m";
			st = "Z for prime p are the only abelian simple groups";
			st = "Z for prime p are the only abelian simple groups";
			//st = "a nontrivial group is simple if it contains no nontrivial normal subgroups";
			
			//String[] strAr = p1.preprocess("F is a extension over Q".split(" "));			
			
			strAr = st.split("\\,|\\.");
			for(int i = 0; i < strAr.length; i++){
				ThmP1.parse(ThmP1.tokenize(ThmP1.preprocess(strAr[i].trim().split(" ")))); //p1.parse(p1.tokenize(p1.preprocess(strAr2)));
			}
			
			//p1.parse(p1.tokenize(p1.preprocess("characteristic of Fp is p".split(" "))));
						
		}
		
}
