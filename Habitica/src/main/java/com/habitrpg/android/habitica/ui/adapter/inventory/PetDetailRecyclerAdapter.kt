package com.habitrpg.android.habitica.ui.adapter.inventory

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import com.facebook.drawee.view.SimpleDraweeView
import com.habitrpg.android.habitica.R
import com.habitrpg.android.habitica.events.commands.FeedCommand
import com.habitrpg.android.habitica.extensions.inflate
import com.habitrpg.android.habitica.extensions.notNull
import com.habitrpg.android.habitica.models.inventory.Mount
import com.habitrpg.android.habitica.models.inventory.Pet
import com.habitrpg.android.habitica.ui.helpers.DataBindingUtils
import com.habitrpg.android.habitica.ui.helpers.bindView
import com.habitrpg.android.habitica.ui.menu.BottomSheetMenu
import com.habitrpg.android.habitica.ui.menu.BottomSheetMenuItem
import io.reactivex.BackpressureStrategy
import io.reactivex.Flowable
import io.reactivex.subjects.PublishSubject
import io.realm.OrderedRealmCollection
import io.realm.RealmRecyclerViewAdapter
import io.realm.RealmResults
import org.greenrobot.eventbus.EventBus

class PetDetailRecyclerAdapter(data: OrderedRealmCollection<Pet>?, autoUpdate: Boolean) : RealmRecyclerViewAdapter<Pet, PetDetailRecyclerAdapter.PetViewHolder>(data, autoUpdate) {

    var itemType: String? = null
    var context: Context? = null
    private var existingMounts: RealmResults<Mount>? = null
    private val equipEvents = PublishSubject.create<String>()

    fun getEquipFlowable(): Flowable<String> {
        return equipEvents.toFlowable(BackpressureStrategy.DROP)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PetViewHolder {
        return PetViewHolder(parent.inflate(R.layout.pet_detail_item))
    }

    override fun onBindViewHolder(holder: PetViewHolder, position: Int) {
        data.notNull {
            holder.bind(it[position])
        }
    }

    fun setExistingMounts(existingMounts: RealmResults<Mount>) {
        this.existingMounts = existingMounts
        notifyDataSetChanged()
    }

    inner class PetViewHolder(itemView: View) : androidx.recyclerview.widget.RecyclerView.ViewHolder(itemView), View.OnClickListener {
        var animal: Pet? = null

        private val imageView: SimpleDraweeView by bindView(R.id.imageView)
        private val titleView: TextView by bindView(R.id.titleTextView)
        private val trainedProgressbar: ProgressBar by bindView(R.id.trainedProgressBar)

        private val isOwned: Boolean
            get() = this.animal?.trained ?: 0 > 0

        private val canRaiseToMount: Boolean
            get() {
                for (mount in existingMounts ?: emptyList<Mount>()) {
                    if (mount.key == animal?.key) {
                        return !mount.owned
                    }
                }
                return false
            }

        init {
            itemView.setOnClickListener(this)
        }

        fun bind(item: Pet) {
            this.animal = item
            this.titleView.text = item.color
            this.trainedProgressbar.visibility = if (animal?.animalGroup == "specialPets") View.GONE else View.VISIBLE
            this.imageView.alpha = 1.0f
            if (this.animal?.trained ?: 0 > 0) {
                if (this.canRaiseToMount) {
                    this.trainedProgressbar.progress = animal?.trained ?: 0
                } else {
                    this.trainedProgressbar.visibility = View.GONE
                }
                DataBindingUtils.loadImage(this.imageView, "Pet-" + itemType + "-" + item.color)
            } else {
                this.trainedProgressbar.visibility = View.GONE
                if (this.animal?.trained == 0) {
                    DataBindingUtils.loadImage(this.imageView, "PixelPaw")
                } else {
                    DataBindingUtils.loadImage(this.imageView, "Pet-" + itemType + "-" + item.color)
                }
                this.imageView.alpha = 0.3f
            }
        }

        override fun onClick(v: View) {
            if (!this.isOwned) {
                return
            }
            val context = context ?: return
            val menu = BottomSheetMenu(context)
            menu.addMenuItem(BottomSheetMenuItem(itemView.resources.getString(R.string.use_animal)))
            if (animal?.animalGroup != "specialPets" && canRaiseToMount) {
                menu.addMenuItem(BottomSheetMenuItem(itemView.resources.getString(R.string.feed)))
            }
            menu.setSelectionRunnable { index ->
                if (index == 0) {
                    animal.notNull {
                        equipEvents.onNext(it.key)
                    }
                } else if (index == 1) {
                    val event = FeedCommand()
                    event.usingPet = animal
                    EventBus.getDefault().post(event)
                }
            }
            menu.show()
        }
    }
}
