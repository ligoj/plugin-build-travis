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
			blue: 'fa fa-circle',
			red: 'fa fa-circle',
			disabled: 'fa fa-ban',
			yellow: 'fa fa-circle'
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
			result += '<button class="service-build-travis-build btn-link"><i class="fa fa-play" data-toggle="tooltip" title="' + current.$messages['service:build:travis:build'] + '"></i></button>';
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
			var clazz = (current.jobStatusColor[job.status] || 'text-muted') + ' ' + (job.building ? 'fa-refresh fa-spin' : current.jobStatusTypo[job.status] || 'fa fa-circle');
			return '<i data-toggle="tooltip" title="' + title + '" class="' + clazz + '"></i>';
		},

		configureSubscriptionParameters: function (configuration) {
			if (configuration.mode === 'create') {
				current.$super('registerXServiceSelect2')(configuration, 'service:build:travis:template-job', 'service/build/travis/template/');
				configuration.validators['service:build:travis:job'] = current.validateJobCreateMode;
			} else {
				current.$super('registerXServiceSelect2')(configuration, 'service:build:travis:job', 'service/build/travis/');
			}
		},

		/**
		 * Live validation of job name.
		 */
		validateJobCreateMode: function () {
			validationManager.reset(_('service:build:travis:job'));
			var $input = _('service:build:travis:job');
			var jobName = $input.val();
			$input.closest('.form-group').find('.form-control-feedback').remove().end().addClass('has-feedback');
			var pkey = current.$super('model').pkey;
			if(jobName.match('^(?:'+ pkey + '|' + pkey + '-[a-z0-9]*)$') === null) {
				validationManager.addError($input, {
					rule: 'validation-job-name',
					parameters: current.$super('model').pkey
				}, 'job', true);
				return false;
			}
			// Live validation to check the group does not exists
			validationManager.addMessage($input, null, [], null, 'fa fa-refresh fa-spin');
			$.ajax({
				dataType: 'json',
				url: REST_PATH + 'service/build/travis/' + current.$super('getSelectedNode')() + '/job/' + jobName,
				type: 'GET',
				global: false,
				success: function () {
					// Existing project
					validationManager.addError(_('service:build:travis:job'), {
						rule: 'already-exist',
						parameters: [current.$messages['service:build:travis:job'], jobName]
					}, 'job', true);
				},
				error: function() {
					// Succeed, not existing project
					validationManager.addSuccess(_('service:build:travis:job'), [], null, true);
				}
			});

			// For now return true for the immediate validation system, even if the Ajax call may fail
			return true;
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
