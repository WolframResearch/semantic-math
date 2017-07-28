package thmp.parse;

import java.util.BitSet;

import com.google.common.hash.HashFunction;
import com.google.common.hash.Hashing;
import com.google.common.hash.*;

/**
 * Bit string representation of command, composes with BitSet.
 * Key is to have a way of probabilistic comparison. 
 * 
 * @author yihed
 */
public class CommandBits {
	private static BitSet bitSet;

	public static void main(String[] args){
		/*bitSet = new BitSet(4);
		bitSet.set(1);
		bitSet.set(3);
		System.out.println(bitSet);
		
		HashFunction hf = Hashing.md5();
		System.out.println(hf.hashString("pre").asInt());
		Funnel<CharSequence> funnel = Funnels.stringFunnel();
		BloomFilter<CharSequence> filter = BloomFilter.create(funnel, 5);
		filter.put("pre");
		System.out.print(filter);
		System.out.println(filter.mightContain("pre"));*/
	}
	
}
