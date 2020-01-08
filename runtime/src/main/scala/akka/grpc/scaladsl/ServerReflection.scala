/*
 * Copyright (C) 2020 Lightbend Inc. <https://www.lightbend.com>
 */

package akka.grpc.scaladsl

import akka.actor.ActorSystem
import akka.grpc.ServiceObject
import akka.grpc.internal.ServerReflectionImpl
import akka.http.scaladsl.model.{ HttpRequest, HttpResponse }
import akka.stream.Materializer

import grpc.reflection.v1alpha.reflection.ServerReflectionHandler

object ServerReflection {
  def apply(objects: List[ServiceObject])(
      implicit mat: Materializer,
      sys: ActorSystem): HttpRequest => scala.concurrent.Future[HttpResponse] =
    ServerReflectionHandler.apply(ServerReflectionImpl(objects.map(_.descriptor), objects.map(_.name)))
  def partial(objects: List[ServiceObject])(
      implicit mat: Materializer,
      sys: ActorSystem): PartialFunction[HttpRequest, scala.concurrent.Future[HttpResponse]] =
    ServerReflectionHandler.partial(ServerReflectionImpl(objects.map(_.descriptor), objects.map(_.name)))
}
