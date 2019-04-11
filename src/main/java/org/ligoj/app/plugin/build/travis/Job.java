package org.ligoj.app.plugin.build.travis;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Travis job definition.
 */
@Getter
@Setter
public class Job extends DescribedBean<String> {

	/**
	 * SID
	 */
	private static final long serialVersionUID = 1L;

	private String status;
	private boolean building;
	private String lastBuildId;
}
