package org.aksw.iguana.cc.worker.impl;

import org.aksw.iguana.cc.config.elements.Connection;
import org.aksw.iguana.cc.model.QueryExecutionStats;
import org.aksw.iguana.cc.query.set.QuerySet;
import org.aksw.iguana.cc.worker.impl.update.UpdateTimer;
import org.aksw.iguana.commons.annotation.Nullable;
import org.aksw.iguana.commons.annotation.Shorthand;
import org.apache.http.HttpHeaders;
import org.apache.http.client.config.RequestConfig;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.aksw.iguana.commons.constants.COMMON;

import java.io.File;
import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Properties;

import static org.aksw.iguana.commons.time.TimeUtils.durationInMilliseconds;

/**
 * 
 * A Worker using SPARQL Updates to create service request.
 * 
 * @author f.conrads
 *
 */
@Shorthand("UPDATEWorker")
public class UPDATEWorker extends HttpGetWorker {

	private String contentType = "application/sparql-update";

	private int currentQueryID = 0;
	private UpdateTimer updateTimer = new UpdateTimer();
	private String timerStrategy;

	public UPDATEWorker(String taskID, Connection connection, String queriesFile,
						@Nullable String timerStrategy, @Nullable Integer timeOut, @Nullable Integer timeLimit,
						@Nullable Integer fixedLatency, @Nullable Integer gaussianLatency, Integer workerID) {
		super(taskID, connection, queriesFile, null, "update",
				"lang.SPARQL", timeOut, timeLimit, fixedLatency, gaussianLatency, "UPDATEWorker", workerID);
		this.contentType=contentType;
		this.timerStrategy=timerStrategy;
	}

	/*
	 Updated from HttpGetMethod
	 */
	void buildRequest(String query, String queryID) throws UnsupportedEncodingException {
		StringEntity entity = new StringEntity(query);
		request = new HttpPost(con.getUpdateEndpoint());
		((HttpPost) request).setEntity(entity);
		request.setHeader("Content-Type", contentType);
		RequestConfig requestConfig = RequestConfig.custom()
				.setSocketTimeout(timeOut.intValue())
				.setConnectTimeout(timeOut.intValue())
				.build();

		if (this.responseType != null)
			request.setHeader(HttpHeaders.ACCEPT, this.responseType);

		request.setConfig(requestConfig);
	}

	@Override
	public void startWorker(){
		setUpdateTimer(this.timerStrategy);
		super.startWorker();
	}

	@Override
	public void waitTimeMs() {
		double wait = this.updateTimer.calculateTime(durationInMilliseconds(startTime, Instant.now()), this.tmpExecutedQueries);
		LOGGER.debug("Worker[{{}} : {{}}]: Time to wait for next Query {{}}", workerType, workerID, wait);
		try {
			Thread.sleep((long)wait);
		} catch (InterruptedException e) {
			LOGGER.error("Worker[{{}} : {{}}]: Could not wait time before next query due to: {{}}", workerType,
					workerID, e);
			LOGGER.error("", e);
		}
		super.waitTimeMs();
	}

	@Override
	public synchronized void addResults(QueryExecutionStats results)
	{
			// create Properties store it in List
			Properties result = new Properties();
			result.setProperty(COMMON.EXPERIMENT_TASK_ID_KEY, this.taskID);
			result.put(COMMON.RECEIVE_DATA_TIME, results.getExecutionTime());
			result.put(COMMON.RECEIVE_DATA_SUCCESS, results.getResponseCode());
			result.put(COMMON.RECEIVE_DATA_SIZE, results.getResultSize());
			result.put(COMMON.QUERY_HASH, queryHash);
			result.setProperty(COMMON.QUERY_ID_KEY, results.getQueryID());
			result.put(COMMON.PENALTY, this.timeOut);
			// Add extra Meta Key, worker ID and worker Type
			result.put(COMMON.EXTRA_META_KEY, this.extra);
			setResults(result);
			executedQueries++;
	}

	@Override
	public void getNextQuery(StringBuilder queryStr, StringBuilder queryID) throws IOException {
		// If there are no more updates, send end signal
		if (this.currentQueryID >= this.queryFileList.length) {
			this.stopSending();
			return;
		}
		// get next query
		QuerySet currentQueryFile = this.queryFileList[this.currentQueryID++];
		queryID.append(currentQueryFile.getName());

		int queriesInFile = currentQueryFile.size();
		int queryLine = 0;
		if (queriesInFile > 1) {
			queryLine = queryChooser.nextInt(queriesInFile);
		}
		queryStr.append(currentQueryFile.getQueryAtPos(queryLine));
	}

	@Override
	public void setQueriesList(QuerySet[] queries) {
		super.setQueriesList(queries);
		this.currentQueryID = 0;
	}

	/**
	 * Sets Update Timer according to strategy
	 * 
	 * @param strategyStr
	 *            The String representation of a UpdateTimer.Strategy
	 */
	private void setUpdateTimer(String strategyStr) {
		if (strategyStr == null)
			return;
		UpdateTimer.Strategy strategy = UpdateTimer.Strategy.valueOf(strategyStr.toUpperCase());
		switch (strategy) {
		case FIXED:
			if (timeLimit != null) {
				this.updateTimer = new UpdateTimer(this.timeLimit/this.queryFileList.length);
			} else {
				LOGGER.warn("Worker[{{}} : {{}}]: FIXED Updates can only be used with timeLimit!", workerType,
						workerID);
			}
			break;
		case DISTRIBUTED:
			if (timeLimit != null) {
				this.updateTimer = new UpdateTimer(this.queryFileList.length, (double) this.timeLimit);
			} else {
				LOGGER.warn("Worker[{{}} : {{}}]: DISTRIBUTED Updates can only be used with timeLimit!", workerType,
						workerID);
			}
			break;
		default:
			break;
		}
		LOGGER.debug("Worker[{{}} : {{}}]: UpdateTimer was set to UpdateTimer:{{}}", workerType, workerID, updateTimer);
	}


	/**
	 * Checks if one queryMix was already executed, as it does not matter how many mixes should be executed
	 * @param noOfQueryMixes
	 * @return
	 */
	@Override
	public boolean hasExecutedNoOfQueryMixes(Long noOfQueryMixes){
		return getExecutedQueries() / (getNoOfQueries() * 1.0) >= 1;
	}

}
