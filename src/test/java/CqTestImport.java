import static org.junit.Assert.*;

import org.junit.Test;

import garnerlee.Cq;

public class CqTestImport {

	@Test public void testHandleCommandImport() {
	    String[] args = {"import", "listing-details.csv"};
	    
	    assertTrue("handleCommand import should return 'true'", Cq.handleCommand(args));
	}

}
