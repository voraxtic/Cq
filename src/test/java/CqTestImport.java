import static org.junit.Assert.*;

import org.junit.Test;

import garnerlee.Cq;

public class CqTestImport {

	@Test public void testHandleCommandImport() {
	    String[] args = {"import", "listing-details.csv"};
	    
	    assertTrue("handleCommand import should return 'true'", Cq.handleCommand(args));
	}

	// Disabled. Firebase API documentation testing only.
	@Test public void testHandleCommandImportTiny() { 
	    String[] args = {"import", "listing-details-tiny.csv"};
	    
	    assertTrue("handleCommand import should return 'true'", Cq.handleCommand(args));
	}
	
}
