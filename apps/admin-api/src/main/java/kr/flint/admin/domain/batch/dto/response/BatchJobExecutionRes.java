package kr.flint.admin.domain.batch.dto.response;

import org.springframework.batch.core.JobExecution;

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
}
