package net.atos.webtools.tapestry.core.util;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.jar.Attributes;
import java.util.jar.JarFile;
import java.util.jar.Manifest;
import java.util.zip.ZipEntry;

import net.atos.webtools.tapestry.core.TapestryCore;



/**
 * This classloader allows us to close out underlying jars,
 * so that projects can be deleted even if they contain
 * factory jars that are in use.<P>
 * 
 * This classloader caches open jars while it is in use,
 * and once closed it will close those jars. It can still be used
 * after that point, but it will open and close on each classloader
 * operation.
 * 
 * NOTE: this come from a org.eclipse.jdt.apt.core ClassLoader
 * 
 */
public class JarClassLoader extends ClassLoader {
	// This is nulled out when the classloader is closed
	private List<JarFile> _jars;
	private final LinkedHashSet<File> _files;
	private List<JarCLInputStream> _openStreams = new LinkedList<JarCLInputStream>();
	private boolean _open = true;
	
	/**
	 * creates a new classloader from jar and parent
	 * 
	 * @param jarFiles : list of jars
	 * @param parent : parent classloader
	 */
	public JarClassLoader(List<File> jarFiles, final ClassLoader parent) {
		super(parent);
		// Handle manifest classpath entries
		_files = new LinkedHashSet<File>(jarFiles);
		for (File f : jarFiles) {
			_recursiveGetManifestJars(f, _files);
		}
		open();
	}
	
	private void open() {
		// Create all jar files
		_jars = new ArrayList<JarFile>(_files.size());
		for (File f : _files) {
			try {
				JarFile jar = new JarFile(f);
				_jars.add(jar);
			}
			catch (IOException ioe) {
				TapestryCore.logError(ErrorMessages.UNABLE_TO_CREATE_JAR_FILE_FOR_FILE + f, ioe); //$NON-NLS-1$
			}
		}
	}
	
	public synchronized void close() {
		if (! _open) return;
		_open = false;
		
		for (JarCLInputStream st : _openStreams) {
			try {
				st.close();
			}
			catch (IOException ioe) {
				TapestryCore.logError(ErrorMessages.FAILED_TO_CLOSE_STREAM, ioe); //$NON-NLS-1$
			}
		}
		_openStreams = null;

		for (JarFile jar : _jars) {
			try {
				jar.close();
			}
			catch (IOException ioe) {
				TapestryCore.logError(ErrorMessages.FAILED_TO_CLOSE_JAR + jar, ioe); //$NON-NLS-1$
			}
		}
		_jars = null;
	}	
	
	private InputStream openInputStream(InputStream in) {
		JarCLInputStream result = new JarCLInputStream(in);
		_openStreams.add(result);
		return in;
	}
	
	private synchronized void closeInputStream(JarCLInputStream in) {
		if (_open)
			_openStreams.remove(in);
	}
	
	@Override
	protected synchronized Class<?> findClass(String name) throws ClassNotFoundException {
		if (!_open)
			throw new ClassNotFoundException(ErrorMessages.CLASSLOADER_CLOSED + name); //$NON-NLS-1$

		byte[] b = loadClassData(name);
		if (b == null) {
			throw new ClassNotFoundException(ErrorMessages.COULD_NOT_FIND_CLASS + name); //$NON-NLS-1$
		}
		Class<?> clazz = defineClass(name, b, 0, b.length);
		// Define the package if necessary
		String pkgName = getPackageName(name);
		if (pkgName != null) {
			Package pkg = getPackage(pkgName);
			if (pkg == null) {
				definePackage(pkgName, null, null, null, null, null, null, null);
			}
		}
		return clazz;
	}
	
	private String getPackageName(String fullyQualifiedName) {
		int index = fullyQualifiedName.lastIndexOf('.');
		if (index != -1) {
			return fullyQualifiedName.substring(0, index);
		}
		return null;
	}
	
	// returns null if no class found
	private byte[] loadClassData(String name) {
		name = name.replace('.','/');
		InputStream input = getResourceAsStream(name + ".class"); //$NON-NLS-1$
		if (input == null)
			return null;
		try {
			ByteArrayOutputStream baos = new ByteArrayOutputStream();
			byte[] buf = new byte[1024];
	        int len;
	        while ((len = input.read(buf)) > 0) {
	            baos.write(buf, 0, len);
	        }
	        baos.close();
	        return baos.toByteArray();
		}
		catch (IOException ioe) {
			return null;
		}
		finally {
			try {
				input.close();
				} 
			catch (IOException ioe) {
			}
		}		
	}
	
	@Override
	public synchronized InputStream getResourceAsStream(String name) {
		InputStream input = getParent().getResourceAsStream(name);
		if (input != null)
			return input;
		
		if (!_open) 
			return null;
	
		for (JarFile j : _jars) {
			try {
				ZipEntry entry = j.getEntry(name);
				if (entry != null) {
					InputStream zipInput = j.getInputStream(entry);
					return openInputStream(zipInput);
				}
			}
			catch (IOException ioe) {
				TapestryCore.logError(ErrorMessages.UNABLE_TO_GET_ENTRY_FROM_JAR + j, ioe); //$NON-NLS-1$
			}
		}
		return null;
	}
	
	/**
	 * This is difficult to implement and close out resources underneath.
	 * Delaying until someone actually requests this.
	 * 
	 * If we actually contain the entry throw UnsupportedOperationException,
	 * else return null in case another classloader can handle this.
	 */
	@Override
	public URL getResource(String name) {
		for (JarFile j : _jars) {
			ZipEntry entry = j.getEntry(name);
			if (entry != null) {
				throw new UnsupportedOperationException(ErrorMessages.GET_RESOURCE_NOT_IMPLEMENTED + name); //$NON-NLS-1$
			}
		}
		return null;
	}

	/**
	 * This is difficult to implement and close out resources underneath.
	 * Delaying until someone actually requests this.
	 */
	@Override
	public Enumeration<URL> getResources(String name) throws IOException {
		throw new UnsupportedOperationException(ErrorMessages.GET_RESOURCES_NOT_IMPLEMENTED); //$NON-NLS-1$
	}
	
	private class JarCLInputStream extends InputStream {
		
		private boolean _closed = false;
		
		private final InputStream _input;
		
		public JarCLInputStream(InputStream origInput) {
			_input = origInput;
		}

		@Override
		public void close() throws IOException {
 			if (_closed) {
				// NOOP
				return;
			}
			try {
				super.close();
				_input.close();
				_closed = true;
			}
			finally {
				closeInputStream(this);
			}
		}

		@Override
		public int read() throws IOException {
			return _input.read();
		}

		@Override
		public int available() throws IOException {
			return _input.available();
		}

		@Override
		public synchronized void mark(int readlimit) {
			_input.mark(readlimit);
		}

		@Override
		public boolean markSupported() {
			return _input.markSupported();
		}

		@Override
		public int read(byte[] b, int off, int len) throws IOException {
			return _input.read(b, off, len);
		}

		@Override
		public int read(byte[] b) throws IOException {
			return _input.read(b);
		}

		@Override
		public synchronized void reset() throws IOException {
			_input.reset();
		}

		@Override
		public long skip(long n) throws IOException {
			return _input.skip(n);
		}
	}
	
	/**
	 * Scan manifest classpath entries of a jar, adding all found jar files
	 * to the set of manifest jars.
	 */
    private static void _recursiveGetManifestJars(File jarFile, Set<File> manifestJars) {
        if (!jarFile.exists())
            return;

        JarFile jar = null;
        try {
            jar = new JarFile(jarFile);
            Manifest mf = jar.getManifest();
            if (mf == null)
                return;
            String classpath = mf.getMainAttributes().getValue(Attributes.Name.CLASS_PATH);
            if (classpath == null)
                return;

            // We've got some entries
            File parent = jarFile.getParentFile();

            String[] rgPaths = classpath.split(" "); //$NON-NLS-1$
            for (String path : rgPaths)
            {
                if (path.length() == 0)
                    continue;
                File file = new File(parent, path);
                // If we haven't seen this, we need to get its manifest jars as well
                if (!manifestJars.contains(file) && file.exists()) {
                    manifestJars.add(file);
                    _recursiveGetManifestJars(file, manifestJars);
                }
            }
        }
        catch (IOException ioe) {
        }
        finally {
            if (jar != null) {
                try {jar.close();} catch (IOException ioe) {}
            }
        }
    }
}