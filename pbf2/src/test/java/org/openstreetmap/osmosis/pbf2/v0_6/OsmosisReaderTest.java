// This software is released into the Public Domain.  See copying.txt for details.
package org.openstreetmap.osmosis.pbf2.v0_6;

import java.io.File;
import java.io.IOException;

import org.junit.Test;
import org.openstreetmap.osmosis.core.Osmosis;
import org.openstreetmap.osmosis.testutil.AbstractDataTest;


/**
 * Tests the OsmosisReader and OsmosisSerializer classes.
 * 
 * @author Brett Henderson
 */
public class OsmosisReaderTest extends AbstractDataTest {
	/**
	 * Tests writing to and reading from PBF files.
	 * 
	 * @throws IOException
	 *             if any file operations fail.
	 */
	@Test
	public void testWriteAndRead() throws IOException {
		// Generate data files.
		File inputXmlFile = dataUtils.createDataFile("v0_6/data-snapshot.osm");
		File pbfFile = dataUtils.newFile();
		File outputXmlFile = dataUtils.newFile();

        // Read the XML and write to PBF using the standard PBF writer.
        Osmosis.run(new String[] {
        		"-q",
        		"--read-xml-0.6",
        		inputXmlFile.getPath(),
        		"--write-pbf-0.6",
        		pbfFile.getPath()
                });
        
        // Read the PBF using the PBF2 reader and write to XML.
        Osmosis.run(new String[] {
        		"-q",
        		"--read-pbf-fast-0.6",
        		pbfFile.getPath(),
        		"--write-xml-0.6",
        		outputXmlFile.getPath()
                });

        // Validate that the output file matches the input file.
        dataUtils.compareFiles(inputXmlFile, outputXmlFile);
	}
}
