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

package org.polypheny.db.monitoring.core;

import lombok.extern.slf4j.Slf4j;

/**
 * Provider for the MonitoringService singleton instance.
 */
@Slf4j
public class MonitoringServiceProvider {

    private static MonitoringService INSTANCE = null;


    public static MonitoringService MONITORING_SERVICE() {
        if (INSTANCE == null) {
            INSTANCE = MonitoringServiceFactory.CreateMonitoringService();
        }
        return INSTANCE;
    }

    //Additional Method to be consequent with other Instantiation invocations
    public MonitoringService getInstance(){
        return this.MONITORING_SERVICE();
    }
}