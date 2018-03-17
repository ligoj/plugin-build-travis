define(function () {
	var current = {

		/**
		 * Job status to color class
		 * @type {Object} {String} to {String}
		 */
		jobStatusColor: {
			blue: 'text-success',
			red: 'text-danger',
			disabled: 'text-muted',
			yellow: 'text-warning'
		},
		/**
		 * Job status to style for not building mode
		 * @type {Object} {String} to {String}
		 */
		jobStatusTypo: {
			blue: 'fas fa-circle',
			red: 'fas fa-circle',
			disabled: 'fas fa-ban',
			yellow: 'fas fa-circle'
		},

		initialize: function () {
			current.$super('$view').on('click', '.service-build-travis-build', current.serviceBuildTravisBuild);
		},

		/**
		 * Render travis project name.
		 */
		renderKey: function (subscription) {
			return current.$super('renderKey')(subscription, 'service:build:travis:job');
		},

		/**
		 * Render Build travis data.
		 */
		renderFeatures: function (subscription) {
			var result = current.$super('renderServicelink')('home', subscription.parameters['service:build:travis:url-site'] + subscription.parameters['service:build:travis:job'], 'service:build:travis:job', undefined, ' target="_blank"');
			result += '<button class="service-build-travis-build btn-link"><i class="fas fa-play" data-toggle="tooltip" title="' + current.$messages['service:build:travis:build'] + '"></i></button>';
			// Help
			result += current.$super('renderServiceHelpLink')(subscription.parameters, 'service:build:help');
			return result;
		},

		/**
		 * Render travis details : name and display name.
		 */
		renderDetailsKey: function (subscription) {
			return current.$super('generateCarousel')(subscription, [
				[
					'name', subscription.data.job.name || subscription.parameters['service:build:travis:job']
				],
				[
					'description', subscription.data.job.description || '' 
				]
			], 0);
		},

		/**
		 * Display the status of the job, including the building state
		 */
		renderDetailsFeatures: function (subscription) {
			var job = subscription.data.job;
			var title = (current.$messages['service:build:travis:status-' + job.status] || job.status) + (job.building ? ' (' + current.$messages['service:build:travis:building'] + ')' : '');
			var clazz = (current.jobStatusColor[job.status] || 'text-muted') + ' ' + (job.building ? 'fa-sync-alt fa-spin' : current.jobStatusTypo[job.status] || 'fas fa-circle');
			return '<i data-toggle="tooltip" title="' + title + '" class="' + clazz + '"></i>';
		},

		configureSubscriptionParameters: function (configuration) {
			current.$super('registerXServiceSelect2')(configuration, 'service:build:travis:job', 'service/build/travis/');
		},

		/**
		 * Launch the travis's job for the associated subscription's id
		 */
		serviceBuildTravisBuild: function () {
			var subscription = $(this).closest('tr').attr('data-id');
			var job = current.$super('subscriptions').fnGetData($(this).closest('tr')[0]);
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/build/travis/build/' + subscription,
				type: 'POST',
				success: function () {
					notifyManager.notify(Handlebars.compile(current.$messages['travis-build-job-success'])((job.parameters && job.parameters['service:build:travis:job']) || subscription));
				}
			});
		}
	};
	return current;
});
