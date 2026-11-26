.class public final Lcom/android/systemui/statusbar/ConsentFast;
.super Ljava/lang/Object;
.source "ConsentFast.java"


# annotations
.annotation system Ldalvik/annotation/MemberClasses;
    value = {
        Lcom/android/systemui/statusbar/ConsentFast$Fast;
    }
.end annotation


# static fields
.field static final CLOSE_MS:J = 0x3e8L

.field static final MSG_CONFIRM:I = 0x1

.field static final TAG:Ljava/lang/String; = "ConsentFast"


# direct methods
.method private constructor <init>()V
    .registers 1

    .line 25
    invoke-direct {p0}, Ljava/lang/Object;-><init>()V

    return-void
.end method

.method public static hook(Ljava/lang/Object;)V
    .registers 6

    .line 30
    const-string v0, "ConsentFast"

    :try_start_2
    invoke-virtual {p0}, Ljava/lang/Object;->getClass()Ljava/lang/Class;

    move-result-object v1

    const-string v2, "mHandler"

    invoke-virtual {v1, v2}, Ljava/lang/Class;->getDeclaredField(Ljava/lang/String;)Ljava/lang/reflect/Field;

    move-result-object v1

    .line 31
    const/4 v2, 0x1

    invoke-virtual {v1, v2}, Ljava/lang/reflect/Field;->setAccessible(Z)V

    .line 32
    invoke-virtual {v1, p0}, Ljava/lang/reflect/Field;->get(Ljava/lang/Object;)Ljava/lang/Object;

    move-result-object v2

    check-cast v2, Landroid/os/Handler;

    .line 33
    instance-of v3, v2, Lcom/android/systemui/statusbar/ConsentFast$Fast;

    if-eqz v3, :cond_1b

    return-void

    .line 34
    :cond_1b
    new-instance v3, Lcom/android/systemui/statusbar/ConsentFast$Fast;

    invoke-virtual {v2}, Landroid/os/Handler;->getLooper()Landroid/os/Looper;

    move-result-object v2

    move-object v4, p0

    check-cast v4, Landroid/os/Handler$Callback;

    invoke-direct {v3, v2, v4}, Lcom/android/systemui/statusbar/ConsentFast$Fast;-><init>(Landroid/os/Looper;Landroid/os/Handler$Callback;)V

    invoke-virtual {v1, p0, v3}, Ljava/lang/reflect/Field;->set(Ljava/lang/Object;Ljava/lang/Object;)V

    .line 35
    const-string p0, "start-up notice auto-close set to 1000 ms"

    invoke-static {v0, p0}, Landroid/util/Log;->i(Ljava/lang/String;Ljava/lang/String;)I
    :try_end_2f
    .catchall {:try_start_2 .. :try_end_2f} :catchall_30

    .line 38
    goto :goto_47

    .line 36
    :catchall_30
    move-exception p0

    .line 37
    new-instance v1, Ljava/lang/StringBuilder;

    invoke-direct {v1}, Ljava/lang/StringBuilder;-><init>()V

    const-string v2, "start-up notice left as stock: "

    invoke-virtual {v1, v2}, Ljava/lang/StringBuilder;->append(Ljava/lang/String;)Ljava/lang/StringBuilder;

    move-result-object v1

    invoke-virtual {v1, p0}, Ljava/lang/StringBuilder;->append(Ljava/lang/Object;)Ljava/lang/StringBuilder;

    move-result-object p0

    invoke-virtual {p0}, Ljava/lang/StringBuilder;->toString()Ljava/lang/String;

    move-result-object p0

    invoke-static {v0, p0}, Landroid/util/Log;->w(Ljava/lang/String;Ljava/lang/String;)I

    .line 39
    :goto_47
    return-void
.end method
