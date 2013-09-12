package net.atos.webtools.tapestry.core.models.features;

import net.atos.webtools.tapestry.core.models.ProjectModel;

import org.eclipse.jdt.core.IType;

/**
 * Tapestry validator model.
 */
public class ValidatorModel extends AbstractFeatureModel {
	
	public ValidatorModel(String prefix, IType type, ProjectModel projectModel, String source, String subPackage) {
		super(prefix, type, projectModel, source, subPackage);
	}
}
