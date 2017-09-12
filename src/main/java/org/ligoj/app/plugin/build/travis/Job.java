package org.ligoj.app.plugin.build.travis;

import org.ligoj.bootstrap.core.DescribedBean;

import lombok.Getter;
import lombok.Setter;

/**
 * Jenkins job definition.
 */
@Getter
@Setter
public class Job extends DescribedBean<String> {

	private String status;
	private boolean building;
	private String lastBuildId;
}
