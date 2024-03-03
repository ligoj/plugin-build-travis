define({
	'service:build:travis:job': 'Projet',
	'service:build:travis:job-description': 'Projet',
	'service:build:travis:url-api-description': 'API endpoint à utiliser, voir <a href="https://docs.travis-ci.com/api">docs.travis-ci.com</a>',
	'service:build:travis:url-site': 'URL',
	'service:build:travis:url-site-description': 'Web URL. Pour les projets open source utiliser https://travis-ci.org/',
	'service:build:travis:api-token-description': 'Exécuter : gem install travis && travis login && travis token',
	'service:build:travis:build': 'Construire',
	'service:build:travis:status-blue': 'Succès',
	'service:build:travis:status-yellow': 'Instable',
	'service:build:travis:status-disabled': 'Inconnu',
	'service:build:travis:status-red': 'Échec',
	'service:build:travis:building': 'En construction',
	'travis-build-job-success': 'Lancement du job {{this}} effectué',
	'error': {
		'travis-job': 'Tâche non trouvée',
		'travis-connection': 'Serveur inatteignable',
		'travis-login': 'Échec de l\'authentification',
		'travis-rights': 'Droits insuffisants pour accéder aux tâches'
	},
	'validation-job-name' : 'Doit commencer par {{this}}-, ne contenir que des caractères minuscules, sans caractères spéciaux'

});
