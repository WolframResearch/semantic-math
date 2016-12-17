package thmp.search;

import java.util.List;

/**
 * Search interface to be implemented by various search methods.
 * Interface useful for dynamic dispatch.
 * 
 * @author yihed
 */
public interface Searcher {

	List<Integer> search(String thm, List<Integer> list);
}
