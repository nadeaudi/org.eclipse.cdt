package org.eclipse.cdt.internal.errorparsers;
/*
 * (c) Copyright IBM Corp. 2000, 2001. All Rights Reserved.
 */
import java.io.File;
import java.util.StringTokenizer;
import org.eclipse.cdt.core.ErrorParserManager;
import org.eclipse.cdt.core.IErrorParser;
import org.eclipse.cdt.core.IMarkerGenerator;
import org.eclipse.core.resources.IFile;
public class VCErrorParser implements IErrorParser {
	public boolean processLine(String line, ErrorParserManager eoParser) {
		// msdev: filname(linenumber) : error/warning error_desc
		int firstColon = line.indexOf(':');
		if (firstColon != -1) {
			/* Guard against drive in Windows platform.  */
			if (firstColon == 1) {
				try {
					String os = System.getProperty("os.name"); //$NON-NLS-1$
					if (os != null && os.startsWith("Win")) { //$NON-NLS-1$
						try {
							if (Character.isLetter(line.charAt(0))) {
								firstColon = line.indexOf(':', 2);
							}
						} catch (StringIndexOutOfBoundsException e) {
						}
					}
				} catch (SecurityException e) {
				}
			}
			String firstPart = line.substring(0, firstColon);
			StringTokenizer tok = new StringTokenizer(firstPart, "()"); //$NON-NLS-1$
			if (tok.hasMoreTokens()) {
				String fileName = tok.nextToken();
				if (tok.hasMoreTokens()) {
					String lineNumber = tok.nextToken();
					try {
						int num = Integer.parseInt(lineNumber);
						int i = fileName.lastIndexOf(File.separatorChar);
						if (i != -1) {
							fileName = fileName.substring(i + 1);
						}
						IFile file = eoParser.findFileName(fileName);
						if (file != null || eoParser.isConflictingName(fileName)) {
							String desc = line.substring(firstColon + 1).trim();
							if (file == null) {
								desc = "*" + desc; //$NON-NLS-1$
							}
							int severity = IMarkerGenerator.SEVERITY_ERROR_RESOURCE;
							if (desc.startsWith("warning")) { //$NON-NLS-1$
								severity = IMarkerGenerator.SEVERITY_WARNING;
							}
							eoParser.generateMarker(file, num, desc, severity, null);
							return true;
						}
					} catch (NumberFormatException e) {
					}
				}
			}
		}
		return false;
	}
}
