/*
 * Copyright (C) 2017 - present  Instructure, Inc.
 *
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, version 3 of the License.
 *
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 *
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.instructure.teacher.fragments

import android.os.Bundle
import android.os.Parcelable
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.SearchView
import androidx.recyclerview.widget.RecyclerView
import com.instructure.canvasapi2.models.CanvasContext
import com.instructure.canvasapi2.models.User
import com.instructure.canvasapi2.utils.ApiPrefs
import com.instructure.canvasapi2.utils.pageview.PageView
import com.instructure.interactions.router.Route
import com.instructure.pandautils.analytics.SCREEN_VIEW_PEOPLE_LIST
import com.instructure.pandautils.analytics.ScreenView
import com.instructure.pandautils.binding.viewBinding
import com.instructure.pandautils.fragments.BaseSyncFragment
import com.instructure.pandautils.utils.*
import com.instructure.teacher.R
import com.instructure.teacher.adapters.PeopleListRecyclerAdapter
import com.instructure.teacher.adapters.StudentContextFragment
import com.instructure.teacher.databinding.FragmentPeopleListLayoutBinding
import com.instructure.teacher.databinding.RecyclerSwipeRefreshLayoutBinding
import com.instructure.teacher.dialog.PeopleListFilterDialog
import com.instructure.teacher.factory.PeopleListPresenterFactory
import com.instructure.teacher.holders.UserViewHolder
import com.instructure.teacher.interfaces.AdapterToFragmentCallback
import com.instructure.teacher.presenters.PeopleListPresenter
import com.instructure.teacher.router.RouteMatcher
import com.instructure.teacher.utils.RecyclerViewUtils
import com.instructure.teacher.utils.setupBackButton
import com.instructure.teacher.viewinterface.PeopleListView
import java.util.*

@PageView(url = "{canvasContext}/users")
@ScreenView(SCREEN_VIEW_PEOPLE_LIST)
class PeopleListFragment : BaseSyncFragment<User, PeopleListPresenter, PeopleListView, UserViewHolder, PeopleListRecyclerAdapter>(), PeopleListView, SearchView.OnQueryTextListener {

    private val binding by viewBinding(FragmentPeopleListLayoutBinding::bind)

    private lateinit var swipeRefreshLayoutContainerBinding: RecyclerSwipeRefreshLayoutBinding

    private val canvasContext: CanvasContext by ParcelableArg(key = Const.CANVAS_CONTEXT)
    private var canvasContextsSelected: ArrayList<CanvasContext>? = null

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        swipeRefreshLayoutContainerBinding = RecyclerSwipeRefreshLayoutBinding.bind(view)
        super.onViewCreated(view, savedInstanceState)
    }

    override fun layoutResId(): Int = R.layout.fragment_people_list_layout

    override fun onCreateView(view: View) {}

    override fun onReadySetGo(presenter: PeopleListPresenter) {
        recyclerView?.adapter = adapter

        setupViews()
        presenter.loadData(false)
    }

    override fun onHandleBackPressed() = binding.peopleListToolbar.closeSearch()

    private fun setupViews() = with(binding) {
        val canvasContext = nonNullArgs.getParcelable<CanvasContext>(Const.CANVAS_CONTEXT)
        peopleListToolbar.setTitle(R.string.tab_people)
        peopleListToolbar.subtitle = canvasContext!!.name
        if (peopleListToolbar.menu.size() == 0) peopleListToolbar.inflateMenu(R.menu.menu_people_list)
        val searchView = peopleListToolbar.menu.findItem(R.id.search).actionView as SearchView

        peopleListToolbar.menu.findItem(R.id.search).setOnActionExpandListener(object : MenuItem.OnActionExpandListener {
            override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                filterTitleWrapper.visibility = View.GONE
                swipeRefreshLayoutContainerBinding.swipeRefreshLayout.isEnabled = false
                if (peopleListToolbar.menu.findItem(R.id.peopleFilterMenuItem) != null) {
                    peopleListToolbar.menu.findItem(R.id.peopleFilterMenuItem).isVisible = false
                }
                return true
            }

            override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                filterTitleWrapper.visibility = View.VISIBLE
                swipeRefreshLayoutContainerBinding.swipeRefreshLayout.isEnabled = true
                if (peopleListToolbar.menu.findItem(R.id.peopleFilterMenuItem) != null) {
                    peopleListToolbar.menu.findItem(R.id.peopleFilterMenuItem).isVisible = true
                }
                presenter.refresh(false)
                return true
            }
        })

        searchView.setOnQueryTextListener(this@PeopleListFragment)

        peopleListToolbar.menu.findItem(R.id.peopleFilterMenuItem)?.setOnMenuItemClickListener {
            //let the user select the course/group they want to see
            PeopleListFilterDialog.getInstance(requireActivity().supportFragmentManager, presenter.canvasContextListIds, canvasContext, true) { canvasContexts ->
                canvasContextsSelected = ArrayList()
                canvasContextsSelected!!.addAll(canvasContexts)

                presenter.canvasContextList = canvasContextsSelected as ArrayList<CanvasContext>
                setupTitle(canvasContexts)
            }.show(requireActivity().supportFragmentManager, PeopleListFilterDialog::class.java.simpleName)
            false
        }

        clearFilterTextView.setOnClickListener {
            peopleFilter.setText(R.string.allPeople)
            presenter.clearCanvasContextList()
            clearFilterTextView.visibility = View.GONE
        }

        setupTitle(presenter.canvasContextList)
        ViewStyler.themeToolbarColored(requireActivity(), peopleListToolbar, canvasContext.backgroundColor, requireContext().getColor(R.color.white))
        peopleListToolbar.setupBackButton(this@PeopleListFragment)
    }

    /**
     * Used to set up the title at the top of the recyclerview. If there are filters set it'll use the titles of the canvas requireContext()s
     * as the title. Otherwise it will say "All People"
     *
     * @param canvasContexts
     */
    private fun setupTitle(canvasContexts: ArrayList<CanvasContext>) = with(binding) {
        if (canvasContexts.isEmpty()) {
            peopleFilter.setText(R.string.allPeople)
            clearFilterTextView.visibility = View.GONE
            return
        }
        // Get the title based on Canvas Contexts selected (groups and/or sections)
        val title = StringBuilder()
        for (i in canvasContexts.indices) {
            title.append(canvasContexts[i].name)
            // Only add the comma if it's not the last in the list
            if (i + 1 < canvasContexts.size) {
                title.append(", ")
            }
        }

        peopleFilter.text = title.toString().trim { it <= ' ' }
        clearFilterTextView.visibility = View.VISIBLE
    }


    override fun getPresenterFactory() =
            PeopleListPresenterFactory(nonNullArgs.getParcelable<Parcelable>(Const.CANVAS_CONTEXT) as CanvasContext)


    override fun onPresenterPrepared(presenter: PeopleListPresenter) {
        RecyclerViewUtils.buildRecyclerView(rootView, requireContext(), adapter,
                presenter, R.id.swipeRefreshLayout, R.id.recyclerView, R.id.emptyPandaView, getString(R.string.no_items_to_display_short))
        addSwipeToRefresh(swipeRefreshLayoutContainerBinding.swipeRefreshLayout)
    }

    override fun createAdapter(): PeopleListRecyclerAdapter {
        return PeopleListRecyclerAdapter(requireContext(), presenter, object : AdapterToFragmentCallback<User> {
            override fun onRowClicked(model: User, position: Int) {
                val canvasContext = nonNullArgs.getParcelable<CanvasContext>(Const.CANVAS_CONTEXT)!!
                if (canvasContext.isDesigner()) {
                    showToast(R.string.errorIsDesigner)
                    return
                }
                val bundle = StudentContextFragment.makeBundle(model.id, canvasContext.id, true)
                RouteMatcher.route(requireContext(), Route(null, StudentContextFragment::class.java, canvasContext, bundle))
            }
        })
    }

    override fun onQueryTextSubmit(query: String): Boolean = false

    override fun onQueryTextChange(newText: String): Boolean {
        adapter.clear()
        presenter.searchPeopleList(newText)
        return true
    }

    override val recyclerView: RecyclerView get() = swipeRefreshLayoutContainerBinding.recyclerView

    override fun withPagination(): Boolean = true

    override fun perPageCount(): Int = ApiPrefs.perPageCount

    override fun onRefreshFinished() {
        swipeRefreshLayoutContainerBinding.swipeRefreshLayout.isRefreshing = false
    }

    override fun onRefreshStarted() = with(swipeRefreshLayoutContainerBinding) {
        //this prevents two loading spinners from happening during pull to refresh
        if(!swipeRefreshLayout.isRefreshing) {
            emptyPandaView.visibility  = View.VISIBLE
        }
        emptyPandaView.setLoading()
    }

    override fun checkIfEmpty() {
        RecyclerViewUtils.checkIfEmpty(
            swipeRefreshLayoutContainerBinding.emptyPandaView,
            recyclerView,
            swipeRefreshLayoutContainerBinding.swipeRefreshLayout,
            adapter,
            presenter.isEmpty
        )
    }

    companion object {
        fun newInstance(canvasContext: CanvasContext): PeopleListFragment {
            val args = Bundle()
            args.putParcelable(Const.CANVAS_CONTEXT, canvasContext)
            val fragment = PeopleListFragment()
            fragment.arguments = args
            return fragment
        }
    }
}
