/*
 * Copyright 2019-2021 The Polypheny Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.polypheny.db.monitoring.obsolet.subscriber;


import java.sql.Timestamp;

import lombok.extern.slf4j.Slf4j;
import org.polypheny.db.monitoring.obsolet.MonitorEvent;
import org.polypheny.db.monitoring.obsolet.storage.BackendConnector;

@Slf4j
public class DummySubscriber extends AbstractSubscriber{


    private static final String subscriberName = "DUMMY";



    public DummySubscriber(){
        this.isPersistent = false;
        this.initializeSubscriber();
    }

    //Todo decide whether to create arbitrary backend or use central config one
    public DummySubscriber( BackendConnector backendConnector ){
        this.isPersistent = true;
        this.backendConnector = backendConnector;
        this.initializeSubscriber();
    }

    @Override
    protected void initializeSubscriber() {
        setSubscriberName(this.subscriberName);
    }

    @Override
    protected void initPersistentDB() {

    }

    @Override
    public void handleEvent(MonitorEvent event) {
        log.info("Dummy received event which originated at: " + new Timestamp(event.getRecordedTimestamp()));
    }
}