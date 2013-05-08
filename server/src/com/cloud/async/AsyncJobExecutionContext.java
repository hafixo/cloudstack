// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.async;

import javax.inject.Inject;

import com.cloud.async.dao.AsyncJobJoinMapDao;
import com.cloud.exception.ConcurrentOperationException;
import com.cloud.exception.InsufficientCapacityException;
import com.cloud.exception.ResourceUnavailableException;
import com.cloud.serializer.SerializerHelper;
import com.cloud.utils.component.ComponentContext;

public class AsyncJobExecutionContext  {
	private AsyncJob _job;
	
	@Inject private AsyncJobManager _jobMgr;
	@Inject private AsyncJobJoinMapDao _joinMapDao;
	
	private static ThreadLocal<AsyncJobExecutionContext> s_currentExectionContext = new ThreadLocal<AsyncJobExecutionContext>();

	public AsyncJobExecutionContext() {
	}
	
	public AsyncJobExecutionContext(AsyncJob job) {
		_job = job;
	}
	
	public SyncQueueItem getSyncSource() {
		return _job.getSyncSource();
	}
	
	public AsyncJob getJob() {
		if(_job == null) {
			_job = _jobMgr.getPseudoJob();
		}
		
		return _job;
	}
	
	public void setJob(AsyncJob job) {
		_job = job;
	}
	
    public void completeAsyncJob(int jobStatus, int resultCode, Object resultObject) {
    	assert(_job != null);
    	_jobMgr.completeAsyncJob(_job.getId(), jobStatus, resultCode, resultObject);
    }
    
    public void updateAsyncJobStatus(int processStatus, Object resultObject) {
    	assert(_job != null);
    	_jobMgr.updateAsyncJobStatus(_job.getId(), processStatus, resultObject);
    }
    
    public void updateAsyncJobAttachment(String instanceType, Long instanceId) {
    	assert(_job != null);
    	_jobMgr.updateAsyncJobAttachment(_job.getId(), instanceType, instanceId);
    }
	
	public void logJobJournal(AsyncJob.JournalType journalType, String 
	    journalText, String journalObjJson) {
		assert(_job != null);
		_jobMgr.logJobJournal(_job.getId(), journalType, journalText, journalObjJson);
	}
	
    public void joinJob(long joinJobId) {
    	assert(_job != null);
    	_jobMgr.joinJob(_job.getId(), joinJobId);
    }
    
    //
	// check failure exception before we disjoin the worker job
	// TODO : it is ugly and this will become unnecessary after we switch to full-async mode
	// 
    public void disjoinJob(long joinedJobId) throws InsufficientCapacityException,
		ConcurrentOperationException, ResourceUnavailableException {
    	assert(_job != null);
    	
    	AsyncJobJoinMapVO record = _joinMapDao.getJoinRecord(_job.getId(), joinedJobId);
    	if(record.getJoinStatus() == AsyncJobConstants.STATUS_FAILED && record.getJoinResult() != null) {
    		Object exception = SerializerHelper.fromObjectSerializedString(record.getJoinResult());
    		if(exception != null && exception instanceof Exception) {
    			if(exception instanceof InsufficientCapacityException)
    				throw (InsufficientCapacityException)exception;
    			else if(exception instanceof ConcurrentOperationException)
    				throw (ConcurrentOperationException)exception;
    			else if(exception instanceof ResourceUnavailableException)
    				throw (ResourceUnavailableException)exception;
    			else
    				throw new RuntimeException((Exception)exception);
    		}
    	}
    	
    	_jobMgr.disjoinJob(_job.getId(), joinedJobId);
    }
    
    public void completeJoin(int joinStatus, String joinResult) {
    	assert(_job != null);
    	_jobMgr.completeJoin(_job.getId(), joinStatus, joinResult);
    }
    
    public void completeJobAndJoin(int joinStatus, String joinResult) {
    	assert(_job != null);
    	_jobMgr.completeJoin(_job.getId(), joinStatus, joinResult);
    	_jobMgr.completeAsyncJob(_job.getId(), joinStatus, 0, null);
    }

	public static AsyncJobExecutionContext getCurrentExecutionContext() {
		AsyncJobExecutionContext context = s_currentExectionContext.get();
		if(context == null) {
			context = new AsyncJobExecutionContext();
			context = ComponentContext.inject(context);
			setCurrentExecutionContext(context);
		}
		
		return context;
	}
	
	public static void setCurrentExecutionContext(AsyncJobExecutionContext currentContext) {
		s_currentExectionContext.set(currentContext);
	}
}
