define({
	'root': {
		'service:build:travis:job': 'Project',
		'service:build:travis:job-description': 'Job identifier',
		'service:build:travis:url-api': 'URL API',
		'service:build:travis:url-api-description': 'API endpoint to use, see <a href="https://docs.travis-ci.com/api">docs.travis-ci.com</a>',
		'service:build:travis:url-site': 'URL',
		'service:build:travis:url-site-description': 'Web URL. For open source projects use https://travis-ci.org/',
		'service:build:travis:api-token': 'Access token',
		'service:build:travis:api-token-description': 'Execute : gem install travis && travis login && travis token',
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
