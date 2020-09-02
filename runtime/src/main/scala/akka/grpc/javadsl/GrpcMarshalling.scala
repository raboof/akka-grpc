/*
 * Copyright (C) 2018-2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.javadsl

import java.util.concurrent.{ CompletableFuture, CompletionStage }
import java.util.Optional

import akka.NotUsed
import akka.actor.ActorSystem
import akka.actor.ClassicActorSystemProvider
import akka.grpc._
import akka.grpc.internal._
import akka.grpc.GrpcProtocol.{ GrpcProtocolReader, GrpcProtocolWriter }
import akka.http.javadsl.model.{ HttpRequest, HttpResponse }
import akka.japi.Function
import akka.stream.Materializer
import akka.stream.javadsl.Source
import akka.util.ByteString

import com.github.ghik.silencer.silent

object GrpcMarshalling {

  def negotiated[T](
      req: HttpRequest,
      f: (GrpcProtocolReader, GrpcProtocolWriter) => CompletionStage[T]): Optional[CompletionStage[T]] =
    GrpcProtocol
      .negotiate(req)
      .map {
        case (maybeReader, writer) =>
          maybeReader.map(reader => f(reader, writer)).fold[CompletionStage[T]](failure, identity)
      }
      .fold(Optional.empty[CompletionStage[T]])(Optional.of)

  def unmarshal[T](
      data: Source[ByteString, AnyRef],
      u: ProtobufSerializer[T],
      mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[T] =
    data.via(reader.dataFrameDecoder).map(u.deserialize).runWith(SingleParameterSink.create[T](), mat)

  def unmarshalStream[T](
      data: Source[ByteString, AnyRef],
      u: ProtobufSerializer[T],
      @silent("never used") mat: Materializer,
      reader: GrpcProtocolReader): CompletionStage[Source[T, NotUsed]] = {
    CompletableFuture.completedFuture[Source[T, NotUsed]](
      data
        .mapMaterializedValue(_ => NotUsed)
        .via(reader.dataFrameDecoder)
        .map(japiFunction(u.deserialize))
        // In gRPC we signal failure by returning an error code, so we
        // don't want the cancellation bubbled out
        .via(new CancellationBarrierGraphStage)
        .mapMaterializedValue(japiFunction(_ => NotUsed)))
  }

  def marshal[T](
      e: T,
      m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      eHandler: Function[ActorSystem, Function[Throwable, Trailers]] = GrpcExceptionHandler.defaultMapper)
      : HttpResponse =
    marshalStream(Source.single(e), m, writer, system, eHandler)

  def marshalStream[T](
      e: Source[T, NotUsed],
      m: ProtobufSerializer[T],
      writer: GrpcProtocolWriter,
      system: ClassicActorSystemProvider,
      eHandler: Function[ActorSystem, Function[Throwable, Trailers]] = GrpcExceptionHandler.defaultMapper)
      : HttpResponse =
    GrpcResponseHelpers(e.asScala, scalaAnonymousPartialFunction(eHandler))(m, writer, system)

  private def failure[R](error: Throwable): CompletableFuture[R] = {
    val future: CompletableFuture[R] = new CompletableFuture()
    future.completeExceptionally(error)
    future
  }
}
