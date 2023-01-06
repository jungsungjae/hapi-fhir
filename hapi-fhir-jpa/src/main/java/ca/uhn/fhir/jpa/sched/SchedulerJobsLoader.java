package ca.uhn.fhir.jpa.sched;

import ca.uhn.fhir.jpa.model.sched.IJobScheduler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;

import java.util.Collection;

public class SchedulerJobsLoader {
	private static final Logger ourLog = LoggerFactory.getLogger(SchedulerJobsLoader.class);
	@Autowired
	ApplicationContext myApplicationContext;

	public void scheduleJobs() {
		Collection<IJobScheduler> values = myApplicationContext.getBeansOfType(IJobScheduler.class).values();
		ourLog.info("Scheduling {} jobs in {}", values.size(), myApplicationContext.getId());
		values.forEach(IJobScheduler::scheduleJobs);
	}
}
