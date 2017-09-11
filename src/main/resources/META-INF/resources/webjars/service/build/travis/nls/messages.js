define({
	'root': {
		'service:build:travis:job': 'Job',
		'service:build:travis:url': 'URL',
		'service:build:travis:user': 'User',
		'service:build:travis:api-token': 'API Token',
		'service:build:travis:build': 'Build',
		'service:build:travis:status-blue': 'Success',
		'service:build:travis:status-yellow': 'Unstable',
		'service:build:travis:status-disabled': 'Unknown',
		'service:build:travis:status-red': 'Failure',
		'service:build:travis:building': 'Building',
		'travis-build-job-success': 'Launching the job {{this}} succeed',
		'error': {
			'travis-job': 'Job not found',
			'travis-connection': 'Unreachable server',
			'travis-login': 'Authentication failed',
			'travis-rights': 'No right to read jobs'
		},
		'validation-job-name' : 'Must start with {{this}}-, contain only lower case characters, without special characters'
	},
	'fr': true
});
