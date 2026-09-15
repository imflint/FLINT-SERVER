package kr.flint.api.admin.domain.batch.dto.response;

import org.springframework.batch.core.JobExecution;
import kr.flint.batch.sync.TmdbSyncRun;

public record BatchJobExecutionRes(
	String jobName,
	Long executionId,
	String status,
	String createTime
) {

	public static BatchJobExecutionRes from(JobExecution execution) {
		return new BatchJobExecutionRes(
			execution.getJobInstance().getJobName(),
			execution.getId(),
			execution.getStatus().name(),
			String.valueOf(execution.getCreateTime())
		);
	}

	public static BatchJobExecutionRes from(TmdbSyncRun run) {
		return new BatchJobExecutionRes(
			run.runKey(),
			run.jobExecutionId(),
			run.status().name(),
			String.valueOf(run.createdAt())
		);
	}
}
