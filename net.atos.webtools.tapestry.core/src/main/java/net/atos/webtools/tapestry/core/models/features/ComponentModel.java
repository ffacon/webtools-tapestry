package net.atos.webtools.tapestry.core.models.features;

import net.atos.webtools.tapestry.core.models.ProjectModel;
import net.atos.webtools.tapestry.core.util.Constants;
import net.atos.webtools.tapestry.core.util.InvisibleInstrumentation;

import org.eclipse.jdt.core.IType;

/**
 * Model of a Tapestry Component
 * 
 * This is basically an {@link AbstractParameteredFeatureModel}
 * 
 * @author a160420
 *
 */
public class ComponentModel extends AbstractParameteredFeatureModel{
	
	
	/**
	 * HTML replacement element (Tapestry invisible instrumentation). 
	 */
	private String htmlElement = null;
	
	public String getHtmlElement() {
		return this.htmlElement;
	}
	
	/**
	 * 
	 * @param prefix
	 * @param type
	 * @param projectModel
	 * @param source
	 * @param subPackage
	 */
	public ComponentModel(String prefix, IType type, ProjectModel projectModel, String source, String subPackage) {
		super(prefix, type, projectModel, source, subPackage);
		
		this.htmlElement = InvisibleInstrumentation.getHTMLComponent(name);
	}
	
	/**
	 * WARNING: this constructor is to be used on very special cases, 
	 * use the other one most of the time 
	 * 
	 * @param prefix
	 * @param name
	 * @param javadoc
	 * @param source
	 */
	public ComponentModel(String prefix, String name, String javadoc, String source){
		super();
		if(! Constants.TAPESTRY_CORE.equals(prefix)){
			this.prefix = prefix;
		}
		this.name = name;
		this.javadoc = javadoc;
		this.source = source;
		
		this.subPackage = "";
		
		this.htmlElement = InvisibleInstrumentation.getHTMLComponent(name);
	}
	
}
