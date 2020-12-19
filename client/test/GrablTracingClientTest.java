/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package grabl.tracing.client.test;

import com.google.protobuf.ByteString;
import grabl.tracing.client.GrablTracing;
import grabl.tracing.client.GrablTracingStandard;
import grabl.tracing.protocol.TracingProto;
import grabl.tracing.protocol.TracingServiceGrpc;
import io.grpc.ManagedChannel;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;
import io.grpc.testing.GrpcCleanupRule;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.AdditionalAnswers.delegatesTo;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

public class GrablTracingClientTest {

    @Rule
    public final GrpcCleanupRule grpcCleanup = new GrpcCleanupRule();

    private final TracingServiceGrpc.TracingServiceImplBase serviceImpl =
            mock(TracingServiceGrpc.TracingServiceImplBase.class, delegatesTo(
                    new TracingServiceGrpc.TracingServiceImplBase() {
                        @Override
                        public void create(TracingProto.Analysis.Req request, StreamObserver<TracingProto.Analysis.Res> responseObserver) {
                            responseObserver.onNext(TracingProto.Analysis.Res.newBuilder()
                                    .setAnalysisId(Long.MAX_VALUE)
                                    .build());
                            responseObserver.onCompleted();
                        }
                    }
            ));

    private GrablTracing client;

    @Before
    public void setUp() throws Exception {
        // Generate a unique in-process server name.
        String serverName = InProcessServerBuilder.generateName();

        // Create a server, add service, start, and register for automatic graceful shutdown.
        grpcCleanup.register(InProcessServerBuilder
                .forName(serverName).directExecutor().addService(serviceImpl).build().start());

        // Create a client channel and register for automatic graceful shutdown.
        ManagedChannel channel = grpcCleanup.register(
                InProcessChannelBuilder.forName(serverName).directExecutor().build());

        // Create a HelloWorldClient using the in-process channel;
        client = new GrablTracingStandard(channel);
    }

    @Test
    public void analysis() {
        GrablTracing.Analysis analysis = client.analysis("owner", "repo", "commit", "analysis");

        ArgumentCaptor<TracingProto.Analysis.Req> requestCaptor = ArgumentCaptor.forClass(TracingProto.Analysis.Req.class);

        verify(serviceImpl).create(requestCaptor.capture(), any());

        TracingProto.Analysis.Req req = requestCaptor.getValue();

        assertThat(req.getOwner(), equalTo("owner"));
        assertThat(req.getRepo(), equalTo("repo"));
        assertThat(req.getCommit(), equalTo("commit"));
        assertThat(req.getName(), equalTo("analysis"));
    }
}
