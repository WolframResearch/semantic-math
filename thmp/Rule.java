package thmp;
/**
 * Rule instances connect different parts of speech,
 * with corresponding probabilities
 * 
 * @author yihed
 */

public class Rule {
	//relation between parts of speech, e.g. pre_ent has relation "prep"
	private String relation;
	private double probability;
	
	// probability = 1 if 
	public Rule(String rel){
		this.relation = rel;
		this.probability = 1;
	}

	public Rule(String rel, double prob){
		this.relation = rel;
		this.probability = prob;
	}

	public String relation(){
		return this.relation;
	}
	
	public double prob(){
		return this.probability;
	}

	@Override
	public String toString() {
		return "Rule [relation=" + relation + ", probability=" + probability + "]";
	}

	// set relation
	public void set_relation(String rel){
		this.relation = rel;
	}

	// set probability
	public void set_prob(double prob){
		this.probability = prob;
	}
	
}
