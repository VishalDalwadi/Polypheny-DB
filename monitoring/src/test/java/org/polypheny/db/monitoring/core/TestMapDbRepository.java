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

import java.io.File;
import org.polypheny.db.monitoring.persistence.MapDbRepository;
import org.polypheny.db.util.FileSystemManager;


public class TestMapDbRepository extends MapDbRepository {

    private static final String FILE_PATH = "testDb";
    private static final String FOLDER_NAME = "monitoring";


    @Override
    public void initialize() {
        this.reset();
        super.initialize( FILE_PATH, FOLDER_NAME );
    }


    private void reset() {
        File folder = FileSystemManager.getInstance().registerNewFolder( FOLDER_NAME );
        new File( folder, FILE_PATH ).delete();
    }

}
